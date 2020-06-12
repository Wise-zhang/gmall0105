package com.atguigu.gmall.interceptors;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.annotations.LoginRequired;
import com.atguigu.gmall.util.CookieUtil;
import com.atguigu.gmall.util.HttpclientUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthInterceptor extends HandlerInterceptorAdapter {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 拦截代码
        // 判断被拦截的请求的访问方法的注释（是否是需要拦截的方法）
        HandlerMethod hm = (HandlerMethod) handler;
        LoginRequired methodAnnotation = hm.getMethodAnnotation(LoginRequired.class);

        // 是否拦截
        if (methodAnnotation == null) {
            return true;
        }
        String token = "";

        String oldToken = CookieUtil.getCookieValue(request, "oldToken", true);
        if (StringUtils.isNotBlank(oldToken)) {
            token = oldToken;
        }

        String newToken = request.getParameter("token");
        if (StringUtils.isNotBlank(newToken)) {
            token = newToken;
        }

        // 是否必须登录
        boolean loginSuccess = methodAnnotation.loginSuccess();

        // 调用认证中心进行校验
        String success = "failure";
        Map<String, String> successMap = new HashMap<>();
        if (StringUtils.isNotBlank(token)) {
            String ip = request.getHeader("x-forwarded-for");
            if (StringUtils.isBlank(ip)) {
                ip = request.getRemoteAddr();
                if (StringUtils.isBlank(ip)) {
                    ip = "127.0.0.1";
                }
            }
            // 有token才校验校验
            String successJson = HttpclientUtil.doGet("http://passport.gmall.com:8086/verify?token=" + token + "&currentIp=" + ip);
            successMap = JSON.parseObject(successJson, Map.class);
            success = successMap.get("status");
        }

        if (loginSuccess) {
            // 必须登录成功才能访问
            assert success != null;
            if (!success.equals("success")) {
                // 重定向到passport登录
                StringBuffer requestURL = request.getRequestURL();// 获取当前请求的URL
                response.sendRedirect("http://passport.gmall.com:8086/index?ReturnUrl=" + requestURL);
                return false;
            }
            // 需要将token携带的用户信息写入
            handleToken(request, response, token, successMap);
        } else {
            // 没有登录也能用，但是必须验证
            assert success != null;
            if (success.equals("success")) {
                // 需要将token携带的用户信息写入
                handleToken(request, response, token, successMap);
                return true;
            }

        }
        return true;
    }

    private void handleToken(HttpServletRequest request, HttpServletResponse response, String token, Map<String, String> successMap) {
        request.setAttribute("memberId", successMap.get("memberId"));
        request.setAttribute("nickname", successMap.get("nickname"));
        // 验证通过就覆盖cookie中的token
        if (StringUtils.isNotBlank(token)) {
            CookieUtil.setCookie(request, response, "oldToken", token, 60 * 60 * 2, true);
        }
    }
}
