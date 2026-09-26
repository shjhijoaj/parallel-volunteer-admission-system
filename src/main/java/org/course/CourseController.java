package org.course;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CourseController {
    private final CourseService service;
    public CourseController(CourseService service) { this.service=service; }
    private static final String USER="COURSE_USER";
    @SuppressWarnings("unchecked") private Map<String,Object> current(HttpSession s) { Object u=s.getAttribute(USER); if(u==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录"); return (Map<String,Object>)u; }
    private Map<String,Object> admin(HttpSession s) { Map<String,Object> u=current(s); if(!"ADMIN".equals(u.get("role"))) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"仅课程管理员可操作"); return u; }
    @PostMapping("/auth/register") public Map<String,Object> register(@RequestBody Map<String,Object> b,HttpSession s,HttpServletRequest request) { Map<String,Object> u=service.register(b);request.changeSessionId();s.setAttribute(USER,u);s.setAttribute("PASSWORD_VERSION",service.credentials(u.get("username").toString()).get("password_hash"));return Map.of("user",u); }
    @PostMapping("/auth/login") public Map<String,Object> login(@RequestBody Map<String,Object> b,HttpSession s,HttpServletRequest request) {
        String name=CourseService.str(b,"username",32,true).toLowerCase(Locale.ROOT),password=CourseService.str(b,"password",128,true);Map<String,Object> c=service.credentials(name);
        if(c==null||!Passwords.matches(password,c.get("password_hash").toString())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"账号或密码错误");
        Map<String,Object> u=service.user(CourseService.id(c.get("id")));request.changeSessionId();s.setAttribute(USER,u);s.setAttribute("PASSWORD_VERSION",c.get("password_hash"));return Map.of("user",u);
    }
    @PostMapping("/auth/logout") public Map<String,Object> logout(HttpSession s) {s.invalidate();return Map.of("ok",true);}
    @GetMapping("/auth/me") public Map<String,Object> me(HttpSession s) {return Map.of("user",current(s));}
    @PostMapping("/auth/password") public Map<String,Object> password(@RequestBody Map<String,Object>b,HttpSession s) {Map<String,Object>u=current(s);service.changePassword(CourseService.id(u.get("id")),b);s.invalidate();return Map.of("ok",true);}
    @GetMapping("/rounds") public Map<String,Object> rounds(HttpSession s) { Map<String,Object> u=(Map<String,Object>)s.getAttribute(USER);return Map.of("rounds",service.rounds(u==null?null:CourseService.id(u.get("id")),u!=null&&"ADMIN".equals(u.get("role")))); }
    @GetMapping("/rounds/{id}") public Map<String,Object> round(@PathVariable long id,HttpSession s) {Map<String,Object>u=(Map<String,Object>)s.getAttribute(USER);return service.overview(id,u==null?null:CourseService.id(u.get("id")),u!=null&&"ADMIN".equals(u.get("role")));}
    @PostMapping("/rounds") public Map<String,Object> create(@RequestBody Map<String,Object>b,HttpSession s){Map<String,Object>u=admin(s);return Map.of("id",service.createRound(CourseService.id(u.get("id")),b));}
    @PutMapping("/rounds/{id}") public Map<String,Object> edit(@PathVariable long id,@RequestBody Map<String,Object>b,HttpSession s){Map<String,Object>u=admin(s);service.editRound(id,CourseService.id(u.get("id")),b);return Map.of("ok",true);}
    @PostMapping("/rounds/{id}/courses") public Map<String,Object> addCourse(@PathVariable long id,@RequestBody Map<String,Object>b,HttpSession s){Map<String,Object>u=admin(s);service.saveCourse(id,null,CourseService.id(u.get("id")),b);return Map.of("ok",true);}
    @PutMapping("/rounds/{rid}/courses/{cid}") public Map<String,Object> editCourse(@PathVariable long rid,@PathVariable long cid,@RequestBody Map<String,Object>b,HttpSession s){Map<String,Object>u=admin(s);service.saveCourse(rid,cid,CourseService.id(u.get("id")),b);return Map.of("ok",true);}
    @DeleteMapping("/rounds/{rid}/courses/{cid}") public Map<String,Object> deleteCourse(@PathVariable long rid,@PathVariable long cid,HttpSession s){Map<String,Object>u=admin(s);service.deleteCourse(rid,cid,CourseService.id(u.get("id")));return Map.of("ok",true);}
    @PostMapping("/rounds/{id}/import") public Map<String,Object> importCourses(@PathVariable long id,@RequestBody Map<String,Object>b,HttpSession s){Map<String,Object>u=admin(s);Object raw=b.get("courses");if(!(raw instanceof List<?>))CourseService.fail("请传入课程列表");List<Map<String,Object>> rows=new ArrayList<>();for(Object x:(List<?>)raw){if(!(x instanceof Map))CourseService.fail("课程格式无效");rows.add((Map<String,Object>)x);}service.importCourses(id,CourseService.id(u.get("id")),rows);return Map.of("count",rows.size());}
    @PostMapping("/rounds/{id}/{action}") public Map<String,Object> transition(@PathVariable long id,@PathVariable String action,HttpSession s){Map<String,Object>u=admin(s);service.transition(id,CourseService.id(u.get("id")),action);return Map.of("ok",true);}
    @PostMapping("/rounds/{id}/apply") public Map<String,Object> apply(@PathVariable long id,@RequestBody Map<String,Object>b,HttpSession s){Map<String,Object>u=current(s);if("ADMIN".equals(u.get("role")))CourseService.fail("管理员不能代学生报名");service.apply(id,CourseService.id(u.get("id")),b);return Map.of("ok",true);}
    @DeleteMapping("/rounds/{id}/apply") public Map<String,Object> withdraw(@PathVariable long id,HttpSession s){Map<String,Object>u=current(s);service.withdraw(id,CourseService.id(u.get("id")));return Map.of("ok",true);}
    @PostMapping("/rounds/{rid}/applications/{aid}/score") public Map<String,Object> score(@PathVariable long rid,@PathVariable long aid,@RequestBody Map<String,Object>b,HttpSession s){Map<String,Object>u=admin(s);service.score(rid,aid,CourseService.id(u.get("id")),b);return Map.of("ok",true);}
}
