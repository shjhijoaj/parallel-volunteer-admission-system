package org.enroll.interceptor;

import org.enroll.configuration.LoginProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录校验拦截器。
 *
 * <p>登录成功后把标记写入当前 HttpSession，后续请求只要能在自己的 Session 中读到该标记，
 * 就说明是同一个已登录用户。相比早期把 Session 对象存进全局 Map 的写法，
 * 这种方式不会出现多用户互相覆盖、Session 无法释放的问题。</p>
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    /** Session 中保存登录标记的键。 */
    public static final String SESSION_KEY = "authSession";

    @Autowired
    private LoginProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"010\",\"data\":null,\"message\":\"未登录\"}");
        return false;
    }
}
