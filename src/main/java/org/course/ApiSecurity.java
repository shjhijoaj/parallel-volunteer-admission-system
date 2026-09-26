package org.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.*;

@Component
public class ApiSecurity extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final CourseService service;
    private final Map<String,ArrayDeque<Long>> attempts=new HashMap<>();
    public ApiSecurity(ObjectMapper json,CourseService service){this.json=json;this.service=service;}
    private synchronized boolean limited(String address){long now=System.currentTimeMillis();attempts.values().forEach(q->{while(!q.isEmpty()&&q.peekFirst()<now-600000)q.removeFirst();});attempts.entrySet().removeIf(e->e.getValue().isEmpty());if(attempts.size()>=10000&&!attempts.containsKey(address))return true;ArrayDeque<Long> q=attempts.computeIfAbsent(address,k->new ArrayDeque<>());if(q.size()>=30)return true;q.addLast(now);return false;}
    private void error(HttpServletResponse r,int status,String message)throws IOException{r.setStatus(status);r.setContentType("application/json;charset=UTF-8");json.writeValue(r.getWriter(),Map.of("message",message));}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("X-Frame-Options","DENY");res.setHeader("Referrer-Policy","same-origin");
        String path=req.getRequestURI();if(!path.startsWith("/api/")){chain.doFilter(req,res);return;}
        res.setHeader("Cache-Control","no-store");
        if(path.equals("/api/health")){try{service.jdbc().queryForObject("SELECT 1",Integer.class);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getWriter(),Map.of("status","ready","application","课选"));}catch(Exception e){error(res,503,"数据库暂不可用");}return;}
        HttpSession session=req.getSession();if(session.getAttribute("CSRF")==null)session.setAttribute("CSRF",Passwords.token());
        if(path.equals("/api/session")&&req.getMethod().equals("GET")){res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getWriter(),Map.of("csrf",session.getAttribute("CSRF")));return;}
        Object user=session.getAttribute("COURSE_USER");if(user instanceof Map){Map<?,?> u=(Map<?,?>)user;Map<String,Object> credentials=service.credentials(u.get("username").toString());if(credentials==null||!Objects.equals(credentials.get("password_hash"),session.getAttribute("PASSWORD_VERSION"))){session.invalidate();error(res,401,"密码已更改，请重新登录");return;}}
        if(!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())){
            if(!Objects.equals(session.getAttribute("CSRF"),req.getHeader("X-CSRF-Token"))){error(res,403,"页面会话已过期，请刷新后再试");return;}
            String origin=req.getHeader("Origin");String expected=req.getScheme()+"://"+req.getHeader("Host");if(origin!=null&&!origin.equals(expected)){error(res,403,"不允许跨站提交");return;}
            if(path.startsWith("/api/auth/")&&!path.endsWith("logout")&&limited(req.getRemoteAddr())){res.setHeader("Retry-After","600");error(res,429,"尝试过于频繁，请 10 分钟后再试");return;}
        }
        chain.doFilter(req,res);
    }
}
