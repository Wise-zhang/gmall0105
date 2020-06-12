package com.atguigu.gmall.passport.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.util.HttpclientUtil;

import java.util.HashMap;
import java.util.Map;

public class TestOauth2 {

    public static String getCode() {

        // 1 获得授权码

        String s1 = HttpclientUtil.doGet("https://api.weibo.com/oauth2/authorize?client_id=2518127757&response_type=code&redirect_uri=http://passport.gmall.com:8086/vlogin");

        System.out.println(s1);


        return null;
    }

    public static String getAccess_token() {
        String s3 = "https://api.weibo.com/oauth2/access_token?";
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("client_id", "2518127757");
        paramMap.put("client_secret", "9e5022335953ef22a3540fd38964ca29");
        paramMap.put("grant_type", "authorization_code");
        paramMap.put("redirect_uri", "http://passport.gmall.com:8086/vlogin");
        paramMap.put("code", "b882d988548ed2b9174af641d20f0dc1");
        String access_token_json = HttpclientUtil.doPost(s3, paramMap);

        Map<String, String> access_map = JSON.parseObject(access_token_json, Map.class);

        assert access_map != null;
        System.out.println(access_map.get("access_token"));
        System.out.println(access_map.get("uid"));

        return access_map.get("access_token");
    }

    public static Map<String, String> getUser_info() {

        // 4 用access_token查询用户信息
        String s4 = "https://api.weibo.com/2/users/show.json?access_token=2.00HMAs7H0p5_hMdbefcb34140Lydjf&uid=6809985023";
        String user_json = HttpclientUtil.doGet(s4);
        Map<String, String> user_map = JSON.parseObject(user_json, Map.class);

        System.out.println(user_map.get("1"));

        return user_map;
    }


    public static void main(String[] args) {

        getUser_info();

    }
}
