package org.course;

import org.enroll.EnrollSystemApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=EnrollSystemApplication.class,properties={"spring.datasource.url=jdbc:h2:mem:course_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","app.admin-password=TestOnlyPass1234","app.credential-file=./target/test-admin.txt"})
@AutoConfigureMockMvc
class CourseWorkflowTest {
    @Autowired CourseService s;
    @Autowired MockMvc mvc;
    long uid(){return CourseService.id(s.register(new HashMap<>(Map.of("username","s"+UUID.randomUUID().toString().replace("-","").substring(0,20),"display_name","测试学生","password","StudentTest1234"))).get("id"));}
    long round(){return s.createRound(1,new HashMap<>(Map.of("title","测试选课批次","description","仅自动化测试","deadline","2029-12-30T12:00")));}
    long course(long r,int capacity){s.saveCourse(r,null,1,new HashMap<>(Map.of("title","课程"+UUID.randomUUID().toString().substring(0,8),"teacher","老师","location","A201","schedule","周六上午","description","测试","capacity",capacity)));List<Map<String,Object>> c=s.repository().courses(r);return CourseService.id(c.get(c.size()-1).get("id"));}
    void apply(long r,long u,List<Long> prefs,boolean adjust){s.apply(r,u,new HashMap<>(Map.of("preferences",prefs,"allow_adjust",adjust)));}
    MockHttpSession session(long uid){Map<String,Object>u=s.user(uid);MockHttpSession session=new MockHttpSession();session.setAttribute("CSRF","test-csrf");session.setAttribute("COURSE_USER",u);session.setAttribute("PASSWORD_VERSION",s.credentials(u.get("username").toString()).get("password_hash"));return session;}
    @Test void fullWorkflowHonorsPriorityPreferenceAdjustmentAndCapacity(){
        long r=round(),c1=course(r,1),c2=course(r,1);s.transition(r,1,"open");long low=uid(),high=uid(),overflow=uid();
        apply(r,low,List.of(c1),true);apply(r,high,List.of(c1,c2),false);apply(r,overflow,List.of(c1),false);
        long aid=CourseService.id(s.repository().applications(r).stream().filter(a->CourseService.id(a.get("user_id"))==high).findFirst().orElseThrow().get("id"));
        s.score(r,aid,1,Map.of("priority_score",90));s.transition(r,1,"close");s.transition(r,1,"allocate");
        Map<String,Object> before=(Map<String,Object>)s.overview(r,high,false).get("application");assertEquals(-2,before.get("result_type"));assertFalse(before.containsKey("assigned_course"));
        s.transition(r,1,"publish");List<Map<String,Object>> all=s.repository().applications(r);assertEquals(1,all.get(0).get("result_type"));assertEquals(high,CourseService.id(all.get(0).get("user_id")));assertEquals(7,all.get(1).get("result_type"));assertEquals(-1,all.get(2).get("result_type"));
        for(Map<String,Object>c:s.repository().courses(r))assertTrue(((Number)c.get("allocated")).intValue()<=((Number)c.get("capacity")).intValue());
        assertThrows(ResponseStatusException.class,()->s.transition(r,1,"allocate"));
    }
    @Test void rejectDuplicateOrCrossBatchPreferencesAndKeepOriginal(){long r=round(),c=course(r,2),other=course(round(),2),u=uid();s.transition(r,1,"open");apply(r,u,List.of(c),false);assertThrows(ResponseStatusException.class,()->apply(r,u,List.of(c,c),false));assertThrows(ResponseStatusException.class,()->apply(r,u,List.of(other),false));assertEquals("["+c+"]",s.repository().applications(r).get(0).get("preferences"));}
    @Test void closeAndDeadlinePreventWrites(){long r=round(),c=course(r,1),u=uid();s.transition(r,1,"open");s.jdbc().update("UPDATE cs_round SET deadline='2020-01-01T12:00' WHERE id=?",r);assertThrows(ResponseStatusException.class,()->apply(r,u,List.of(c),false));assertTrue(s.repository().applications(r).isEmpty());s.transition(r,1,"close");assertThrows(ResponseStatusException.class,()->apply(r,u,List.of(c),false));}
    @Test void importIsAllOrNothing(){long r=round();Map<String,Object>good=new HashMap<>(Map.of("title","合法课程","teacher","老师","location","A201","schedule","周六","capacity",10));Map<String,Object>bad=new HashMap<>(good);bad.put("capacity",-1);assertThrows(ResponseStatusException.class,()->s.importCourses(r,1,List.of(good,bad)));assertTrue(s.repository().courses(r).isEmpty());}
    @Test void concurrentAllocationRunsExactlyOnce()throws Exception{long r=round(),c=course(r,1),u=uid();s.transition(r,1,"open");apply(r,u,List.of(c),false);s.transition(r,1,"close");ExecutorService pool=Executors.newFixedThreadPool(2);try{Callable<Boolean> job=()->{try{s.transition(r,1,"allocate");return true;}catch(ResponseStatusException e){return false;}};List<Future<Boolean>> results=pool.invokeAll(List.of(job,job));assertEquals(1,results.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count());assertEquals(1,((Number)s.repository().courses(r).get(0).get("allocated")).intValue());}finally{pool.shutdownNow();}}
    @Test void studentCannotAdminOrSeeAnotherApplication()throws Exception{long u=uid(),r=round(),c=course(r,2);s.transition(r,1,"open");apply(r,uid(),List.of(c),false);Map<String,Object> view=s.overview(r,u,false);assertNull(view.get("application"));assertFalse(view.containsKey("applications"));mvc.perform(post("/api/rounds/"+r+"/close").session(session(u)).header("X-CSRF-Token","test-csrf")).andExpect(status().isForbidden());mvc.perform(get("/api/rounds/"+r+"/results.xlsx").session(session(u))).andExpect(status().isForbidden());}
    @Test void csrfAndAnonymousAdministrationAreRejected()throws Exception{mvc.perform(post("/api/auth/login").contentType("application/json").content("{}" )).andExpect(status().isForbidden());MockHttpSession anon=new MockHttpSession();anon.setAttribute("CSRF","test-csrf");mvc.perform(post("/api/rounds").session(anon).header("X-CSRF-Token","test-csrf").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());}
    @Test void passwordChangeInvalidatesOtherSessions()throws Exception{long u=uid();MockHttpSession old=session(u);s.changePassword(u,Map.of("old_password","StudentTest1234","new_password","ChangedTest1234"));mvc.perform(get("/api/auth/me").session(old)).andExpect(status().isUnauthorized());}
    @Test void editingPreferencesKeepsRankButWithdrawalRemovesEntry(){long r=round(),c=course(r,2),u=uid();s.transition(r,1,"open");apply(r,u,List.of(c),true);Object id=s.repository().applications(r).get(0).get("id");apply(r,u,List.of(c),false);assertEquals(id,s.repository().applications(r).get(0).get("id"));s.withdraw(r,u);assertTrue(s.repository().applications(r).isEmpty());}
    @Test void healthAndExcelTemplateAreAvailable()throws Exception{long r=round();mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ready"));mvc.perform(get("/api/rounds/"+r+"/template.xlsx").session(session(1))).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));}
}
