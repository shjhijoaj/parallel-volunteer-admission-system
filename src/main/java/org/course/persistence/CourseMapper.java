package org.course.persistence;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** Read-side MyBatis queries. All modifying workflows are transactional in CourseService. */
public interface CourseMapper {
    @Select("SELECT id,title,description,status,deadline,created_at FROM cs_round ORDER BY id DESC")
    List<Map<String,Object>> rounds();
    @Select("SELECT c.id,c.round_id,c.title,c.teacher,c.location,c.schedule,c.description,c.capacity, (SELECT COUNT(*) FROM cs_application a WHERE a.assigned_course=c.id) AS allocated FROM cs_course c WHERE c.round_id=#{roundId} ORDER BY c.id")
    List<Map<String,Object>> courses(@Param("roundId") long roundId);
    @Select("SELECT a.id,a.user_id,u.username,u.display_name,a.preferences,a.allow_adjust,a.priority_score,a.assigned_course,a.result_type,a.submitted_at,c.title AS assigned_title FROM cs_application a JOIN cs_user u ON u.id=a.user_id LEFT JOIN cs_course c ON c.id=a.assigned_course WHERE a.round_id=#{roundId} ORDER BY a.priority_score DESC,a.id")
    List<Map<String,Object>> applications(@Param("roundId") long roundId);
    @Select("SELECT id,action,detail,created_at FROM cs_audit WHERE round_id=#{roundId} ORDER BY id DESC LIMIT 100")
    List<Map<String,Object>> audit(@Param("roundId") long roundId);
}
