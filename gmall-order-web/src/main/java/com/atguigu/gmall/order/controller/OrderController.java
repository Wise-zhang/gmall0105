package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.annotations.LoginRequired;
import com.atguigu.gmall.bean.OmsCartItem;
import com.atguigu.gmall.bean.OmsOrder;
import com.atguigu.gmall.bean.OmsOrderItem;
import com.atguigu.gmall.bean.UmsMemberReceiveAddress;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Controller
public class OrderController {
    @Reference
    private CartService cartService;

    @Reference
    private UserService userService;

    @Reference
    private OrderService orderService;

    @Reference
    private SkuService skuService;

    @RequestMapping("submitOrder")
    @LoginRequired()
    public ModelAndView submitOrder(String receiveAddressId, BigDecimal totalAmount, String tradeCode, HttpServletRequest request, HttpServletResponse response, HttpSession session, ModelMap modelMap) {
        String memberId = (String) request.getAttribute("memberId");
        String nickname = (String) request.getAttribute("nickname");

        // 检验交易码
        String success = orderService.checkTradeCode(memberId, tradeCode);

        if (success.equals("success")) {
            List<OmsOrderItem> omsOrderItems = new ArrayList<>();
            // 订单对象
            OmsOrder omsOrder = new OmsOrder();
            omsOrder.setAutoConfirmDay(7);
            omsOrder.setCreateTime(new Date());
            omsOrder.setMemberId(memberId);
            omsOrder.setMemberUsername(nickname);
            omsOrder.setNote("快点发货");
            // 生成外部订单号
            String outTradeNo = "gmall";
            outTradeNo += System.currentTimeMillis(); // 毫秒时间戳拼接到外部订单号
            SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMDDHHmmss");
            outTradeNo += sdf.format(new Date()); // 将时间字符串拼接到外部订单号
            omsOrder.setOrderSn(outTradeNo);//外部订单号
            omsOrder.setPayAmount(totalAmount);
            omsOrder.setOrderType(1);
            UmsMemberReceiveAddress umsMemberReceiveAddress = userService.getReceiveAddressById(receiveAddressId);
            omsOrder.setReceiverCity(umsMemberReceiveAddress.getCity());
            omsOrder.setReceiverDetailAddress(umsMemberReceiveAddress.getDetailAddress());
            omsOrder.setReceiverName(umsMemberReceiveAddress.getName());
            omsOrder.setReceiverPhone(umsMemberReceiveAddress.getPhoneNumber());
            omsOrder.setReceiverPostCode(umsMemberReceiveAddress.getPostCode());
            omsOrder.setReceiverProvince(umsMemberReceiveAddress.getProvince());
            omsOrder.setReceiverRegion(umsMemberReceiveAddress.getRegion());

            // 当前日期加一天，一天后配送
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DATE, 1);
            Date time = c.getTime();
            omsOrder.setReceiveTime(time);
            omsOrder.setSourceType(0);
            omsOrder.setStatus(0);
            omsOrder.setOrderType(0);
            omsOrder.setTotalAmount(totalAmount);
            // 根据用户id获取要购买的商品列表（购物车）和总价格
            List<OmsCartItem> omsCartItems = cartService.cartList(memberId);

            for (OmsCartItem omsCartItem : omsCartItems) {
                if (omsCartItem.getIsChecked().equals("1")) {
                    OmsOrderItem omsOrderItem = new OmsOrderItem();
                    // 验价
                    boolean b = skuService.checkPrice(omsCartItem.getProductSkuId(), omsCartItem.getPrice());
                    if (!b) {
                        return new ModelAndView("tradeFail");
                    }
                    omsOrderItem.setProductPic(omsCartItem.getProductPic());
                    omsOrderItem.setProductName(omsCartItem.getProductName());

                    omsOrderItem.setOrderSn(outTradeNo);// 外部订单号，用来和其他系统进行交互，防止重复
                    omsOrderItem.setProductCategoryId(omsCartItem.getProductCategoryId());
                    omsOrderItem.setProductPrice(omsCartItem.getPrice());
                    omsOrderItem.setRealAmount(omsCartItem.getTotalPrice());
                    omsOrderItem.setProductQuantity(omsCartItem.getQuantity());
                    omsOrderItem.setProductSkuCode("111111111111");
                    omsOrderItem.setProductSkuId(omsCartItem.getProductSkuId());
                    omsOrderItem.setProductId(omsCartItem.getProductId());
                    omsOrderItem.setProductSn("仓库对应的商品编号");// 在仓库中的skuId

                    omsOrderItems.add(omsOrderItem);
                }
            }
            omsOrder.setOmsOrderItems(omsOrderItems);
            // 将订单和订单详情写入数据库
            // 删除购物车的对应商品
            orderService.saveOrder(omsOrder);
            // 重定向到支付系统
            ModelAndView mv = new ModelAndView("redirect:http://payment.gmall.com:8088/index");
            mv.addObject("outTradeNo", outTradeNo);
            mv.addObject("totalAmount", totalAmount);
            return mv;
        } else {
            return new ModelAndView("tradeFail");
        }

    }

    @RequestMapping("toTrade")
    @LoginRequired()
    public String toTrade(HttpServletRequest request, HttpServletResponse response, HttpSession session, ModelMap modelMap) {
        String memberId = (String) request.getAttribute("memberId");

        // 收件人地址列表
        List<UmsMemberReceiveAddress> umsMemberReceiveAddresses = userService.getReceiveAddressByMemberId(memberId);

        // 用户点击去结算只是将购物车的数据传递给了订单，没有对数据库进行操作
        List<OmsCartItem> omsCartItems = cartService.cartList(memberId);
        ArrayList<OmsOrderItem> omsOrderItems = new ArrayList<>();
        // 将购物车信息添加到订单详情信息中
        for (OmsCartItem omsCartItem : omsCartItems) {
            if (omsCartItem.getIsChecked().equals("1")) {
                OmsOrderItem omsOrderItem = new OmsOrderItem();
                omsOrderItem.setProductName(omsCartItem.getProductName());
                omsOrderItem.setProductPic(omsCartItem.getProductPic());
                omsOrderItems.add(omsOrderItem);
            }
        }
        modelMap.put("userAddressList", umsMemberReceiveAddresses);
        modelMap.put("omsOrderItems", omsOrderItems);
        modelMap.put("totalAmount", getTotalAmount(omsCartItems));

        // 生成交易码，为了提交订单是做交易码校验
        String tradeCode = orderService.genTradeCode(memberId);
        modelMap.put("tradeCode", tradeCode);
        return "trade";
    }

    private BigDecimal getTotalAmount(List<OmsCartItem> omsCartItems) {
        BigDecimal totalAmount = new BigDecimal("0");

        for (OmsCartItem omsCartItem : omsCartItems) {
            BigDecimal totalPrice = omsCartItem.getTotalPrice();

            if (omsCartItem.getIsChecked().equals("1")) {
                totalAmount = totalAmount.add(totalPrice);
            }
        }

        return totalAmount;
    }
}
