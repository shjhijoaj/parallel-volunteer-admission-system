package org.enroll.controller;

import org.enroll.configuration.LoginProperties;
import org.enroll.interceptor.LoginInterceptor;
import org.enroll.utils.JsonResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/login")
public class LoginController {

    @Autowired
    LoginProperties properties;

    @RequestMapping("/doLogin")
    public JsonResponse doLogin(String name, String pass, HttpServletRequest request){
        if (!properties.isEnabled()) {
            return new JsonResponse(JsonResponse.OK, null, "登录校验已关闭");
        }
        if (properties.getAdminName().equals(name) && properties.getAdminPass().equals(pass)) {
            // 登录成功后重建 Session，避免会话固定（Session Fixation）问题。
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute(LoginInterceptor.SESSION_KEY, Boolean.TRUE);
            return new JsonResponse(JsonResponse.OK, null, null);
        }
        return new JsonResponse(JsonResponse.AUTH_ERR, null, "登陆失败");
    }

    @RequestMapping("/checkLogin")
    public JsonResponse checkLogin(HttpSession session){
        if (!properties.isEnabled()) {
            return new JsonResponse(JsonResponse.OK, null, "登录校验已关闭");
        }
        if (Boolean.TRUE.equals(session.getAttribute(LoginInterceptor.SESSION_KEY))){
            return new JsonResponse(JsonResponse.OK, null, "已登录");
        }
        return new JsonResponse(JsonResponse.AUTH_ERR, null, "未登录");
    }

    @RequestMapping("/logout")
    public JsonResponse logout(HttpSession session){
        session.removeAttribute(LoginInterceptor.SESSION_KEY);
        return new JsonResponse(JsonResponse.OK, null, "注销成功");
    }
}
