package com.atguigu.gmall.passport.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.UmsMember;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.util.HttpclientUtil;
import com.atguigu.gmall.util.JwtUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PassportController {

    @Reference
    private UserService userService;

    @RequestMapping("vlogin")
    public String vlogin(String code, HttpServletRequest request) {
        // 授权码换取access_code
        String s3 = "https://api.weibo.com/oauth2/access_token?";
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("client_id", "2518127757");
        paramMap.put("client_secret", "9e5022335953ef22a3540fd38964ca29");
        paramMap.put("grant_type", "authorization_code");
        paramMap.put("redirect_uri", "http://passport.gmall.com:8086/vlogin");
        paramMap.put("code", code);
        String access_token_json = HttpclientUtil.doPost(s3, paramMap);
        Map access_token_map = JSON.parseObject(access_token_json, Map.class);

        // 根据access_token获取用户信息
        assert access_token_map != null;
        String uid = (String) access_token_map.get("uid");
        String access_token = (String) access_token_map.get("access_token");
        String show_user_url = "https://api.weibo.com/2/users/show.json?access_token=" + access_token + "&uid=" + uid;
        String user_json = HttpclientUtil.doGet(show_user_url);
        Map user_map = JSON.parseObject(user_json, Map.class);

        // 将用户信息保存到数据库，用户类型设置为微博用户
        UmsMember umsMember = new UmsMember();
        umsMember.setSourceType("1");
        umsMember.setAccessCode(code);
        umsMember.setAccessToken(access_token);
        umsMember.setSourceUid(uid);
        umsMember.setCity("nanchang");
        umsMember.setNickname((String) user_map.get("screen_name"));
        String g = (String) user_map.get("gender");
        String gender = g.equals("f") ? "0" : "1";
        umsMember.setGender(gender);
        // 判断是否已经注册过
        UmsMember umsCheck = new UmsMember();
        umsCheck.setSourceUid(umsMember.getSourceUid());
        UmsMember umsMemberCheck = userService.checkOauthUser(umsCheck);
        if (umsMemberCheck == null) {
            // 不存在才添加到数据库
            umsMember = userService.addOauthUser(umsMember);
        } else {
            umsMember = umsMemberCheck;
        }
        // 生成jwt的token，并重定向到首页，携带改token
        String token;
        String memberId = umsMember.getId();  // rpc环境下主键返回策略失效;所以要在创建对象时返回该对象
        String nickname = umsMember.getNickname();
        Map<String, Object> userMap = new HashMap<>();
        token = createJWTToken(request, memberId, nickname, userMap);


        return "redirect:http://search.gmall.com:8084/index?token=" + token;
    }

    private String createJWTToken(HttpServletRequest request, String memberId, String nickname, Map<String, Object> userMap) {
        String token;
        userMap.put("memberId", memberId);
        userMap.put("nickname", nickname);
        String ip = request.getHeader("x-forwarded-for");// 通过nginx转发的客户端ip
        if (StringUtils.isBlank(ip)) {
            ip = request.getRemoteAddr();// 从request中获取ip
            if (StringUtils.isBlank(ip)) {
                ip = "127.0.0.1";
            }
        }
        // 按照设计的算法对参数进行加密后，生成token
        token = JwtUtil.encode("2019gmall0105", userMap, ip);
        // 将token存入redis一份
        userService.addUserToken(token, memberId);
        return token;
    }


    @RequestMapping("verify")
    @ResponseBody
    public String verify(String token, String currentIp, HttpServletRequest request) {
        // 通过jwt校验token真假
        Map<String, String> map = new HashMap<>();

        Map<String, Object> decode = JwtUtil.decode(token, "2019gmall0105", currentIp);
        if (decode != null) {
            map.put("status", "success");
            map.put("memberId", (String) decode.get("memberId"));
            map.put("nickname", (String) decode.get("nickname"));
        } else {
            map.put("status", "fail");
        }
        return JSON.toJSONString(map);
    }

    @RequestMapping("login")
    @ResponseBody
    public String login(UmsMember umsMember, HttpServletRequest request) {
        String token;

        // 调用用户服务验证用户名和密码
        UmsMember umsMemberLogin = userService.login(umsMember);
        if (umsMemberLogin != null) {
            // 登录成功
            // 使用jwt生成token
            String memberId = umsMemberLogin.getId();
            String nickname = umsMemberLogin.getNickname();
            HashMap<String, Object> userMap = new HashMap<>();
            token = createJWTToken(request, memberId, nickname, userMap);
        } else {
            token = "fail";
        }

        return token;
    }

    @RequestMapping("index")
    public String index(String ReturnUrl, ModelMap map) {
        map.put("ReturnUrl", ReturnUrl);
        return "index";
    }
}
