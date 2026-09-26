package org.course;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/rounds/{rid}")
public class ExcelController {
    private final CourseService service;
    public ExcelController(CourseService service){this.service=service;}
    long admin(HttpSession s){Object raw=s.getAttribute("COURSE_USER");if(!(raw instanceof Map))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"请先登录");Map<?,?>u=(Map<?,?>)raw;if(!"ADMIN".equals(u.get("role")))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"仅管理员可导入导出");return CourseService.id(u.get("id"));}
    @Data public static class CourseRow {
        @ExcelProperty("课程名称") private String title;
        @ExcelProperty("授课老师") private String teacher;
        @ExcelProperty("上课地点") private String location;
        @ExcelProperty("上课时间") private String schedule;
        @ExcelProperty("名额") private Integer capacity;
        @ExcelProperty("课程介绍") private String description;
    }
    @GetMapping("/template.xlsx") public void template(@PathVariable long rid,HttpSession s,HttpServletResponse response)throws Exception{admin(s);xlsx(response,"course-template.xlsx");CourseRow row=new CourseRow();row.setTitle("示例：Python 实训");row.setTeacher("示例老师");row.setLocation("A201");row.setSchedule("周六 09:00—12:00");row.setCapacity(24);row.setDescription("替换这一行后导入；仅草稿批次允许导入。");EasyExcel.write(response.getOutputStream(),CourseRow.class).sheet("课程模板").doWrite(List.of(row));}
    @PostMapping("/courses.xlsx") public Map<String,Object> upload(@PathVariable long rid,@RequestParam MultipartFile file,HttpSession s)throws Exception{
        long actor=admin(s);if(file.isEmpty()||file.getSize()>2*1024*1024||!Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT).endsWith(".xlsx"))CourseService.fail("请上传不超过 2 MB 的 .xlsx 文件");List<Map<String,Object>> rows=new ArrayList<>();
        try(java.io.InputStream in=file.getInputStream()){EasyExcel.read(in,CourseRow.class,new AnalysisEventListener<CourseRow>(){public void invoke(CourseRow r,AnalysisContext c){if(rows.size()>=200)CourseService.fail("一次最多导入 200 门课程");Map<String,Object> m=new LinkedHashMap<>();m.put("title",r.getTitle());m.put("teacher",r.getTeacher());m.put("location",r.getLocation());m.put("schedule",r.getSchedule());m.put("capacity",r.getCapacity());m.put("description",r.getDescription());rows.add(m);}public void doAfterAllAnalysed(AnalysisContext c){}}).sheet().doRead();}
        catch(org.springframework.web.server.ResponseStatusException e){throw e;}catch(Exception e){CourseService.fail("Excel 格式不正确，请使用下载的模板填写");}
        service.importCourses(rid,actor,rows);return Map.of("count",rows.size());
    }
    @GetMapping("/results.xlsx") public void export(@PathVariable long rid,HttpSession s,HttpServletResponse res)throws Exception{admin(s);service.round(rid,false);List<List<String>> head=List.of(List.of("报名编号"),List.of("账号"),List.of("姓名"),List.of("优先分"),List.of("分配课程"),List.of("结果"));List<List<Object>> data=new ArrayList<>();for(Map<String,Object>a:service.repository().applications(rid)){int t=((Number)a.get("result_type")).intValue();data.add(Arrays.asList(a.get("id"),safe(a.get("username")),safe(a.get("display_name")),a.get("priority_score"),safe(a.get("assigned_title")),t>=1&&t<=6?"第 "+t+" 志愿":t==7?"调剂录取":t==-1?"未分配":"待分配"));}xlsx(res,"course-results.xlsx");EasyExcel.write(res.getOutputStream()).head(head).sheet("分配结果").doWrite(data);}
    static String safe(Object value){String s=Objects.toString(value,"");return s.matches("^[=+@\\-].*")?"'"+s:s;}
    static void xlsx(HttpServletResponse r,String filename){r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename=\""+filename+"\"");}
}
