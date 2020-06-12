package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gmall.annotations.LoginRequired;
import com.atguigu.gmall.bean.OmsOrder;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {
    @Autowired
    private AlipayClient alipayClient;

    @Autowired
    private PaymentService paymentService;

    @Reference
    private OrderService orderService;

    @RequestMapping("index")
    @LoginRequired
    public String index(String outTradeNo, BigDecimal totalAmount, HttpServletRequest request, ModelMap modelMap) {
        String memberId = (String) request.getAttribute("memberId");
        String nickname = (String) request.getAttribute("nickname");

        modelMap.put("nickname", nickname);
        modelMap.put("outTradeNo", outTradeNo);
        modelMap.put("totalAmount", totalAmount);

        return "index";
    }

    @RequestMapping("alipay/submit")
    @LoginRequired()
    @ResponseBody
    public String alipay(String outTradeNo, BigDecimal totalAmount, HttpServletRequest request, ModelMap modelMap) {

        // 获得一个支付宝请求的客户端(它并不是一个链接，而是一个封装好的http的表单请求)
        String form = null;
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();//创建API对应的request

        // 回调函数
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);

        Map<String, Object> map = new HashMap<>();
        map.put("out_trade_no", outTradeNo);
        map.put("product_code", "FAST_INSTANT_TRADE_PAY");
        map.put("total_amount", 0.01);
        map.put("subject", "尚硅谷感光徕卡Pro300瞎命名系列手机");

        String param = JSON.toJSONString(map);

        alipayRequest.setBizContent(param);

        try {
            form = alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单
            System.out.println(form);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        // 生成并且保存用户的支付信息
        OmsOrder omsOrder = orderService.getOrderByOutTradeNo(outTradeNo);
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setOrderId(omsOrder.getId());
        paymentInfo.setOrderSn(outTradeNo);
        paymentInfo.setPaymentStatus("未付款");
        paymentInfo.setSubject("谷粒商城商品一件");
        paymentInfo.setTotalAmount(totalAmount);
        paymentService.savePaymentInfo(paymentInfo);

        // 提交请求到支付宝
        return "支付成功";
    }

    @RequestMapping("alipay/callback/return")
    @LoginRequired()
    public String aliPayCallBackReturn(HttpServletRequest request) {
        // 回调请求中获取支付宝参数
        String sign = request.getParameter("sign");
        String trade_no = request.getParameter("trade_no");
        String out_trade_no = request.getParameter("out_trade_no");

        if (StringUtils.isNotBlank(sign)) {
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setOrderSn(out_trade_no);
            paymentInfo.setPaymentStatus("已支付");
            paymentInfo.setAlipayTradeNo(trade_no);// 支付宝的交易凭证号
            paymentInfo.setCallbackContent("charset=utf-8&out_trade_no=atguigu201903201551591553068319124&method=alipay.trade.page.pay.return&total_amount=0.01&sign=JHMpfB9wv%2FOaNV9Cpjp7%2B6uY83ScfJ4YIG6dsDtrJlbbRJj6Z7%2FMlT3EazeB487wlKGPFim9L2xzIl8MwBpCwOc2qI95pCDwDrXgaO%2F2yA%2FlDp6bDkcRx84Lkm%2F2MNwZ%2FyFSW%2FyyDxWGEI3izHYMm1rf8T6nNDKvfuKTrKiKiIAGSv%2FJX1z7InGW%2BgeWtLlYWdV9fS1aKDEUZwGJaKwQeGf0c2YpZ2u%2FPoBuT32IQTbACx60SO4Jdz4y%2BVjwF5UmvLZD6HP7n5hvcQE833r9FOCU3rOskdAWNWt4wEvaJ%2FAuq%2BFAg6xHTDk1E2iDwkLVjumnYM%2FUcpw6G6Yu60nVtQ%3D%3D&trade_no=2019032022001409701031928056&auth_app_id=2018020102122556&version=1.0&app_id=2018020102122556&sign_type=RSA2&seller_id=2088921750292524&timestamp=2019-03-20+15%3A52%3A40");//回调请求字符串
            paymentInfo.setCallbackTime(new Date());
            // 更新用户的支付状态
            paymentService.updatePayment(paymentInfo);
        }
        return "finish";
    }
}
