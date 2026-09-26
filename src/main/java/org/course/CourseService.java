package org.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.course.persistence.CourseMapper;
import org.enroll.excel.pojo.ExcelMajor;
import org.enroll.excel.pojo.ExcelStudent;
import org.enroll.service.EnrollEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CourseService implements ApplicationRunner {
    final JdbcTemplate db;
    final CourseMapper mapper;
    final ObjectMapper json;
    @Value("${app.admin-password:}") String initialPassword;
    @Value("${app.credential-file}") String credentialFile;
    public CourseService(JdbcTemplate db,CourseMapper mapper,ObjectMapper json) { this.db=db; this.mapper=mapper; this.json=json; }
    public JdbcTemplate jdbc() { return db; }
    public CourseMapper repository() { return mapper; }

    @Override @Transactional(rollbackFor=Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        db.execute("CREATE TABLE IF NOT EXISTS cs_user (id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(32) NOT NULL UNIQUE,display_name VARCHAR(60) NOT NULL,password_hash VARCHAR(250) NOT NULL,role VARCHAR(16) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS cs_round (id BIGINT AUTO_INCREMENT PRIMARY KEY,title VARCHAR(120) NOT NULL,description VARCHAR(2000) NOT NULL,status VARCHAR(16) NOT NULL,deadline VARCHAR(30) NOT NULL,created_at VARCHAR(30) NOT NULL)");
        db.execute("CREATE TABLE IF NOT EXISTS cs_course (id BIGINT AUTO_INCREMENT PRIMARY KEY,round_id BIGINT NOT NULL,title VARCHAR(120) NOT NULL,teacher VARCHAR(60) NOT NULL,location VARCHAR(120) NOT NULL,schedule VARCHAR(120) NOT NULL,description VARCHAR(2000) NOT NULL,capacity INT NOT NULL,FOREIGN KEY(round_id) REFERENCES cs_round(id))");
        db.execute("CREATE TABLE IF NOT EXISTS cs_application (id BIGINT AUTO_INCREMENT PRIMARY KEY,round_id BIGINT NOT NULL,user_id BIGINT NOT NULL,preferences VARCHAR(1000) NOT NULL,allow_adjust BOOLEAN NOT NULL,priority_score INT NOT NULL DEFAULT 0,assigned_course BIGINT,result_type INT NOT NULL DEFAULT -2,submitted_at VARCHAR(30) NOT NULL,UNIQUE(round_id,user_id),FOREIGN KEY(round_id) REFERENCES cs_round(id),FOREIGN KEY(user_id) REFERENCES cs_user(id),FOREIGN KEY(assigned_course) REFERENCES cs_course(id))");
        db.execute("CREATE TABLE IF NOT EXISTS cs_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY,round_id BIGINT NOT NULL,actor BIGINT NOT NULL,action VARCHAR(60) NOT NULL,detail VARCHAR(1000) NOT NULL,created_at VARCHAR(30) NOT NULL)");
        if(db.queryForObject("SELECT COUNT(*) FROM cs_user WHERE role='ADMIN'",Integer.class)==0) {
            String password=initialPassword.isBlank()?Passwords.token():initialPassword;
            checkPassword(password);
            // Save a generated credential before committing the account, never print it.
            if(initialPassword.isBlank()) {
                Path path=Path.of(credentialFile); Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path,"课选本机管理员\n账号：admin\n密码："+password+"\n请登录后修改密码并妥善保管本文件。\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }
            db.update("INSERT INTO cs_user(username,display_name,password_hash,role) VALUES(?,?,?,'ADMIN')","admin","课程管理员",Passwords.hash(password));
        }
        if(db.queryForObject("SELECT COUNT(*) FROM cs_round",Integer.class)==0) {
            db.update("INSERT INTO cs_round(title,description,status,deadline,created_at) VALUES(?,?,'DRAFT',?,?)","实训选课 · 示例批次","这是示例课程目录。老师可编辑课程及截止时间后开放报名；每人每批次分配一门课。优先分相同按报名编号排序，修改志愿不改变编号。",LocalDateTime.now().plusDays(14).withNano(0).toString(),now());
            long id=db.queryForObject("SELECT MAX(id) FROM cs_round",Long.class);
            String[][] samples={{"Python 数据分析实训","数据与应用","实训楼 A201","周六 09:00—12:00","从 CSV 清洗到可视化，完成一份可复现的数据分析报告。"},{"Java Web 项目实践","软件工程","实训楼 A203","周六 09:00—12:00","围绕一个真实业务流程，练习接口、数据库事务与自动化测试。"},{"前端交互设计工作坊","交互与设计","创客空间 B102","周六 09:00—12:00","通过原型、表单验证和响应式页面，把产品想法做成可用界面。"},{"嵌入式入门实训","电子与系统","实验楼 C301","周六 09:00—12:00","学习串口协议、状态机与传感器数据处理，包含仿真实验。"}};
            for(String[] s:samples) db.update("INSERT INTO cs_course(round_id,title,teacher,location,schedule,description,capacity) VALUES(?,?,?,?,?,?,?)",id,s[0],s[1],s[2],s[3],s[4],24);
        }
    }
    static String now() { return LocalDateTime.now().withNano(0).toString(); }
    static void fail(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT,message); }
    static String str(Map<String,Object> m,String key,int max,boolean required) {
        Object raw=m.get(key); if(raw!=null && !(raw instanceof String)) {fail(key+" 必须为文本");}
        String value=raw==null?"":((String)raw).trim();
        if(value.length()>max || (required&&value.isEmpty())) fail(key+" 不能为空或超出长度限制"); return value;
    }
    static int integer(Object raw,int min,int max) {
        if(!(raw instanceof Number) || ((Number)raw).doubleValue()!=((Number)raw).intValue()) { fail("请输入有效整数"); }
        int n=((Number)raw).intValue(); if(n<min||n>max) fail("数值须在 "+min+"～"+max+" 之间"); return n;
    }
    static long id(Object value) { return ((Number)value).longValue(); }
    static void checkPassword(String password) { if(password==null||password.length()<10||password.length()>128) fail("密码须为 10～128 个字符"); }
    Map<String,Object> user(long id) { return db.queryForMap("SELECT id,username,display_name,role FROM cs_user WHERE id=?",id); }
    Map<String,Object> credentials(String username) {
        List<Map<String,Object>> users=db.queryForList("SELECT * FROM cs_user WHERE username=?",username); return users.isEmpty()?null:users.get(0);
    }
    @Transactional
    public Map<String,Object> register(Map<String,Object> body) {
        String name=str(body,"username",32,true).toLowerCase(Locale.ROOT), display=str(body,"display_name",60,true), pass=str(body,"password",128,true);
        if(!name.matches("[a-z0-9_]{3,32}")) fail("账号须为 3～32 位字母、数字或下划线"); checkPassword(pass);
        try { db.update("INSERT INTO cs_user(username,display_name,password_hash,role) VALUES(?,?,?,'STUDENT')",name,display,Passwords.hash(pass)); }
        catch(DuplicateKeyException e) { conflict("账号已存在，请换一个或直接登录"); }
        return user(id(credentials(name).get("id")));
    }
    @Transactional
    public void changePassword(long uid,Map<String,Object> body) {
        String old=str(body,"old_password",128,true), next=str(body,"new_password",128,true);checkPassword(next);
        String stored=db.queryForObject("SELECT password_hash FROM cs_user WHERE id=?",String.class,uid);
        if(!Passwords.matches(old,stored)) fail("原密码不正确");
        db.update("UPDATE cs_user SET password_hash=? WHERE id=?",Passwords.hash(next),uid);
    }
    Map<String,Object> round(long id,boolean lock) {
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM cs_round WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"批次不存在");return rows.get(0);
    }
    void state(Map<String,Object> r,String... allowed) { if(!Arrays.asList(allowed).contains(r.get("status"))) conflict("当前批次状态不允许此操作，请刷新页面"); }
    void audit(long round,long actor,String action,String detail) {db.update("INSERT INTO cs_audit(round_id,actor,action,detail,created_at) VALUES(?,?,?,?,?)",round,actor,action,detail,now());}
    List<Long> prefs(Object raw) {
        if(!(raw instanceof List<?>)) {fail("请按顺序选择课程");}
        List<?> list=(List<?>)raw; if(list.isEmpty()||list.size()>6) fail("请选择 1～6 门意向课程");
        List<Long> result=new ArrayList<>();for(Object x:list) { int n=integer(x,1,Integer.MAX_VALUE); if(result.contains((long)n)) fail("不能重复选择同一门课程"); result.add((long)n); }return result;
    }
    String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    List<Long> decode(Object value) {try{return json.readValue(value.toString(),new TypeReference<List<Long>>(){});}catch(Exception e){throw new IllegalStateException("Stored preferences invalid",e);}}
    public List<Map<String,Object>> rounds(Long uid,boolean admin) { List<Map<String,Object>> rows=mapper.rounds();if(!admin)rows.removeIf(r->"DRAFT".equals(r.get("status")));return rows; }
    public Map<String,Object> overview(long roundId,Long uid,boolean admin) {
        Map<String,Object> r=round(roundId,false);Map<String,Object> out=new LinkedHashMap<>();out.put("round",r);out.put("courses",mapper.courses(roundId));
        if(!admin&&"DRAFT".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"批次尚未开放");
        out.put("applicant_count",db.queryForObject("SELECT COUNT(*) FROM cs_application WHERE round_id=?",Integer.class,roundId));
        List<Map<String,Object>> entries=mapper.applications(roundId);
        for(Map<String,Object> a:entries) a.put("preferences",decode(a.get("preferences")));
        if(admin){out.put("applications",entries);out.put("audit",mapper.audit(roundId));}
        else {
            Map<String,Object> own=entries.stream().filter(a->uid!=null&&id(a.get("user_id"))==uid).findFirst().orElse(null);
            if(own!=null&&!"PUBLISHED".equals(r.get("status"))) {own.remove("assigned_course");own.remove("assigned_title");own.put("result_type",-2);}
            out.put("application",own);
        }
        return out;
    }
    @Transactional
    public void apply(long roundId,long uid,Map<String,Object> body) {
        Map<String,Object> r=round(roundId,true);state(r,"OPEN"); if(LocalDateTime.parse(r.get("deadline").toString()).isBefore(LocalDateTime.now())) conflict("报名已经截止");
        List<Long> choices=prefs(body.get("preferences"));List<Long> available=db.queryForList("SELECT id FROM cs_course WHERE round_id=?",Long.class,roundId);
        if(!available.containsAll(choices)) fail("志愿包含不属于本批次的课程");
        if(!(body.get("allow_adjust") instanceof Boolean)) fail("请确认是否接受调剂");
        int count=db.update("UPDATE cs_application SET preferences=?,allow_adjust=? WHERE round_id=? AND user_id=?",encode(choices),body.get("allow_adjust"),roundId,uid);
        if(count==0) db.update("INSERT INTO cs_application(round_id,user_id,preferences,allow_adjust,submitted_at) VALUES(?,?,?,?,?)",roundId,uid,encode(choices),body.get("allow_adjust"),now());
        audit(roundId,uid,"提交志愿","学生保存了意向课程");
    }
    @Transactional
    public void withdraw(long rid,long uid) {Map<String,Object> r=round(rid,true);state(r,"OPEN");if(LocalDateTime.parse(r.get("deadline").toString()).isBefore(LocalDateTime.now())) conflict("报名已经截止");db.update("DELETE FROM cs_application WHERE round_id=? AND user_id=?",rid,uid);audit(rid,uid,"撤回报名","学生撤回报名；再次报名将取得新的顺序编号");}
    @Transactional
    public long createRound(long actor,Map<String,Object> body) {
        String title=str(body,"title",120,true),desc=str(body,"description",2000,false),deadline=deadline(body);
        org.springframework.jdbc.support.GeneratedKeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(c->{java.sql.PreparedStatement s=c.prepareStatement("INSERT INTO cs_round(title,description,status,deadline,created_at) VALUES(?,?,'DRAFT',?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);s.setString(1,title);s.setString(2,desc);s.setString(3,deadline);s.setString(4,now());return s;},key);
        long rid=Objects.requireNonNull(key.getKey()).longValue();audit(rid,actor,"创建批次",title);return rid;
    }
    String deadline(Map<String,Object> body) {String value=str(body,"deadline",30,true);try{if(!LocalDateTime.parse(value).isAfter(LocalDateTime.now())) fail("截止时间必须在未来");}catch(java.time.format.DateTimeParseException e){fail("截止时间格式无效");}return value;}
    @Transactional
    public void editRound(long rid,long actor,Map<String,Object> body) {state(round(rid,true),"DRAFT");db.update("UPDATE cs_round SET title=?,description=?,deadline=? WHERE id=?",str(body,"title",120,true),str(body,"description",2000,false),deadline(body),rid);audit(rid,actor,"编辑批次","更新说明及截止时间");}
    Map<String,Object> validateCourse(Map<String,Object> body) {Map<String,Object> c=new LinkedHashMap<>();for(String k:List.of("title","teacher","location","schedule","description")) c.put(k,str(body,k,k.equals("description")?2000:(k.equals("teacher")?60:120),!k.equals("description")));c.put("capacity",integer(body.get("capacity"),1,10000));return c;}
    void insertCourse(long rid,Map<String,Object> c) {db.update("INSERT INTO cs_course(round_id,title,teacher,location,schedule,description,capacity) VALUES(?,?,?,?,?,?,?)",rid,c.get("title"),c.get("teacher"),c.get("location"),c.get("schedule"),c.get("description"),c.get("capacity"));}
    @Transactional
    public void saveCourse(long rid,Long cid,long actor,Map<String,Object> body) {
        state(round(rid,true),"DRAFT");Map<String,Object> c=validateCourse(body);
        if(cid==null) insertCourse(rid,c);else if(db.update("UPDATE cs_course SET title=?,teacher=?,location=?,schedule=?,description=?,capacity=? WHERE id=? AND round_id=?",c.get("title"),c.get("teacher"),c.get("location"),c.get("schedule"),c.get("description"),c.get("capacity"),cid,rid)==0) fail("课程不存在");audit(rid,actor,"维护课程",c.get("title").toString());
    }
    @Transactional
    public void importCourses(long rid,long actor,List<Map<String,Object>> bodies) {state(round(rid,true),"DRAFT");if(bodies.isEmpty()||bodies.size()>200)fail("一次导入 1～200 门课程");List<Map<String,Object>> valid=new ArrayList<>();for(Map<String,Object>b:bodies)valid.add(validateCourse(b));for(Map<String,Object>c:valid)insertCourse(rid,c);audit(rid,actor,"批量导入","新增 "+valid.size()+" 门课程");}
    @Transactional
    public void deleteCourse(long rid,long cid,long actor) {state(round(rid,true),"DRAFT");db.update("DELETE FROM cs_course WHERE id=? AND round_id=?",cid,rid);audit(rid,actor,"删除课程","课程编号 "+cid);}
    @Transactional
    public void score(long rid,long aid,long actor,Map<String,Object> body) {state(round(rid,true),"OPEN","CLOSED");int score=integer(body.get("priority_score"),0,1000);if(db.update("UPDATE cs_application SET priority_score=? WHERE id=? AND round_id=?",score,aid,rid)==0)fail("报名记录不存在");audit(rid,actor,"审核优先分","报名 "+aid+" 设置为 "+score+" 分");}
    @Transactional
    public void transition(long rid,long actor,String action) {
        Map<String,Object> r=round(rid,true);String next;
        switch(action) {
            case "open":state(r,"DRAFT");if(mapper.courses(rid).isEmpty())fail("请先添加课程");if(!LocalDateTime.parse(r.get("deadline").toString()).isAfter(LocalDateTime.now()))fail("请先更新报名截止时间");next="OPEN";break;
            case "close":state(r,"OPEN");next="CLOSED";break;
            case "allocate":state(r,"CLOSED");allocate(rid);next="ALLOCATED";break;
            case "publish":state(r,"ALLOCATED");next="PUBLISHED";break;
            default:fail("未知操作");return;
        }
        db.update("UPDATE cs_round SET status=? WHERE id=?",next,rid);audit(rid,actor,action,"批次状态 "+next);
    }
    /** Adapter only: the original allocation engine is kept byte-for-byte unchanged. */
    void allocate(long rid) {
        List<Map<String,Object>> applications=mapper.applications(rid);if(applications.isEmpty())fail("没有报名记录，无法分配");
        List<ExcelMajor> plan=new ArrayList<>();for(Map<String,Object> c:mapper.courses(rid)){ExcelMajor m=new ExcelMajor();m.setMajorId(c.get("id").toString());m.setPlanStudentCount(((Number)c.get("capacity")).intValue());m.setRealisticStudentCount(0);plan.add(m);}
        Map<String,ExcelMajor> index=EnrollEngine.indexPlanById(plan);List<ExcelStudent> students=new ArrayList<>(),waitings=new ArrayList<>();
        for(Map<String,Object> row:applications){ExcelStudent s=new ExcelStudent();s.setAdjust(Boolean.TRUE.equals(row.get("allow_adjust"))?1:0);List<Long> choices=decode(row.get("preferences"));String[] v=new String[6];for(int i=0;i<choices.size();i++)v[i]=choices.get(i).toString();s.setWill1(v[0]);s.setWill2(v[1]);s.setWill3(v[2]);s.setWill4(v[3]);s.setWill5(v[4]);s.setWill6(v[5]);EnrollEngine.apply(s,EnrollEngine.enroll(s,index));students.add(s);if(s.getAcceptedType()==0)waitings.add(s);}
        EnrollEngine.adjust(waitings,plan);
        for(int i=0;i<students.size();i++){ExcelStudent s=students.get(i);db.update("UPDATE cs_application SET assigned_course=?,result_type=? WHERE id=?",s.getAcceptedMajorId()==null?null:Long.valueOf(s.getAcceptedMajorId()),s.getAcceptedType(),applications.get(i).get("id"));}
        for(ExcelMajor m:plan)if(m.getRealisticStudentCount()>m.getPlanStudentCount())throw new IllegalStateException("Capacity invariant failed");
    }
}
