package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.annotations.LoginRequired;
import com.atguigu.gmall.bean.OmsCartItem;
import com.atguigu.gmall.bean.PmsSkuInfo;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.util.CookieUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.alibaba.fastjson.JSON;

@Controller
public class CartController {
    @Reference
    private SkuService skuService;

    @Reference
    private CartService cartService;


    @RequestMapping("checkCart")
    @LoginRequired(loginSuccess = false)
    public String checkCart(String isChecked, String skuId, HttpServletRequest request, HttpServletResponse response, HttpSession session, ModelMap modelMap) {
        List<OmsCartItem> omsCartItems = null;
        String memberId = (String) request.getAttribute("memberId");
        if (StringUtils.isBlank(memberId)) {
            // 没有登录就从cookie中获取
            String cartListCookie = CookieUtil.getCookieValue(request, "cartListCookie", true);
            if (StringUtils.isNotBlank(cartListCookie)) {
                omsCartItems = JSON.parseArray(cartListCookie, OmsCartItem.class);
                for (OmsCartItem omsCartItem : omsCartItems) {
                    if (omsCartItem.getProductSkuId().equals(skuId)) {
                        omsCartItem.setIsChecked(isChecked);
                    }
                }
                // 更新cookie
                CookieUtil.setCookie(request, response, "cartListCookie", JSON.toJSONString(omsCartItems), 60 * 60 * 2, true);
            }

        } else {
            // 调用服务，修改状态
            OmsCartItem omsCartItem = new OmsCartItem();
            omsCartItem.setMemberId(memberId);
            omsCartItem.setProductSkuId(skuId);
            omsCartItem.setIsChecked(isChecked);
            cartService.checkCart(omsCartItem);
            // 将最新的数据从缓存中查出，渲染给内嵌页
            omsCartItems = cartService.cartList(memberId);
        }

        modelMap.put("cartList", omsCartItems);
        // 被勾选商品的总额
        assert omsCartItems != null;
        BigDecimal totalAmount = getTotalAmount(omsCartItems);
        modelMap.put("totalAmount", totalAmount);
        return "cartListInner";
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


    @RequestMapping("cartList")
    @LoginRequired(loginSuccess = false)
    public String cartList(HttpServletRequest request, HttpServletResponse response, HttpSession session, ModelMap modelMap) {
        List<OmsCartItem> omsCartItems = new ArrayList<>();
        String memberId = (String) request.getAttribute("memberId");

        if (StringUtils.isNotBlank(memberId)) {
            // 已经登录查询数据库
            omsCartItems = cartService.cartList(memberId);
        } else {
            // 没有登录就从cookie中获取
            String cartListCookie = CookieUtil.getCookieValue(request, "cartListCookie", true);
            if (StringUtils.isNotBlank(cartListCookie)) {
                omsCartItems = JSON.parseArray(cartListCookie, OmsCartItem.class);
            }
        }
        for (OmsCartItem omsCartItem : omsCartItems) {
            omsCartItem.setTotalPrice(omsCartItem.getPrice().multiply(omsCartItem.getQuantity()));
        }

        modelMap.put("cartList", omsCartItems);
        // 被勾选商品的总额
        BigDecimal totalAmount = getTotalAmount(omsCartItems);
        modelMap.put("totalAmount", totalAmount);
        return "cartList";
    }


    @RequestMapping("addToCart")
    @LoginRequired(loginSuccess = false)
    public String addToCart(String skuId, int quantity, HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        List<OmsCartItem> omsCartItems = new ArrayList<>();

        // 调用商品服务查询商品信息
        PmsSkuInfo skuInfo = skuService.getSkuById(skuId);

        // 将商品信息封装成购物车信息
        OmsCartItem omsCartItem = new OmsCartItem();
        omsCartItem.setCreateDate(new Date());
        omsCartItem.setDeleteStatus(0);
        omsCartItem.setModifyDate(new Date());
        omsCartItem.setPrice(skuInfo.getPrice());
        omsCartItem.setProductAttr("");
        omsCartItem.setProductBrand("");
        omsCartItem.setProductCategoryId(skuInfo.getCatalog3Id());
        omsCartItem.setProductId(skuInfo.getProductId());
        omsCartItem.setProductName(skuInfo.getSkuName());
        omsCartItem.setProductPic(skuInfo.getSkuDefaultImg());
        omsCartItem.setProductSkuCode("11111111111");
        omsCartItem.setProductSkuId(skuId);
        omsCartItem.setQuantity(new BigDecimal(quantity));

        // 判断用户是否登录
        String memberId = (String) request.getAttribute("memberId");

        if (StringUtils.isBlank(memberId)) {
            // 用户没有登录，将购物车数据存储在cookie中

            String cartListCookie = CookieUtil.getCookieValue(request, "cartListCookie", true);
            if (StringUtils.isBlank(cartListCookie)) {
                // cookie为空
                omsCartItems.add(omsCartItem);
            } else {
                // cookie不为空
                omsCartItems = JSON.parseArray(cartListCookie, OmsCartItem.class);
                // 判断添加的购物车数据在cookie中是否存在
                boolean exist = if_cart_exist(omsCartItems, omsCartItem);
                if (exist) {
                    // 之前添加过就更新购物撤的数量
                    for (OmsCartItem cartItem : omsCartItems) {
                        String productSkuId = cartItem.getProductSkuId();
                        if (productSkuId.equals(omsCartItem.getProductSkuId())) {
                            cartItem.setQuantity(cartItem.getQuantity().add(omsCartItem.getQuantity()));
                        }
                    }
                } else {
                    // 之前没有添加就新增当前的购物车
                    omsCartItems.add(omsCartItem);
                }
            }
            // 更新cookie
            CookieUtil.setCookie(request, response, "cartListCookie", JSON.toJSONString(omsCartItems), 60 * 60 * 72, true);
        } else {
            // 用户已经登录
            // 从db中查出购物车数据
            OmsCartItem omsCartItemFromDb = cartService.ifCartExistByUser(memberId, skuId);
            if (omsCartItemFromDb == null) {
                // 该用户没有添加过当前商品
                omsCartItem.setMemberId(memberId);
                omsCartItem.setMemberNickname("test小花");
                omsCartItem.setQuantity(new BigDecimal(quantity));
                cartService.addCart(omsCartItem);
            } else {
                // 该用户添加过当前商品
                omsCartItemFromDb.setQuantity(omsCartItem.getQuantity().add(omsCartItemFromDb.getQuantity()));
                cartService.updateCart(omsCartItemFromDb);
            }
            // 同步缓存
            cartService.flushCartCache(memberId);
        }


        return "success";

    }

    private boolean if_cart_exist(List<OmsCartItem> omsCartItems, OmsCartItem omsCartItem) {
        boolean exist = false;
        for (OmsCartItem cartItem : omsCartItems) {
            String productSkuId = cartItem.getProductSkuId();
            if (productSkuId.equals(omsCartItem.getProductSkuId())) {
                exist = true;
            }
        }
        return exist;
    }

}
