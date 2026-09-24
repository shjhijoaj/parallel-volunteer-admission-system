package org.enroll;

import org.enroll.excel.pojo.ExcelMajor;
import org.enroll.excel.pojo.ExcelStudent;
import org.enroll.mapper.DepartmentMapper;
import org.enroll.mapper.MajorMapper;
import org.enroll.mapper.StatusMapper;
import org.enroll.mapper.StudentMapper;
import org.enroll.pojo.Department;
import org.enroll.pojo.EnrollStatus;
import org.enroll.pojo.StudentResult;
import org.enroll.service.EnrollEngine;
import org.enroll.service.interfaces.IExcelService;
import org.enroll.service.interfaces.IStudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端集成测试：真实连接 MySQL，覆盖
 * "导入招生计划 -> 导入考生志愿 -> 平行志愿投档 -> 专业调剂 -> 导出结果" 的完整流程。
 *
 * <p>该用例会清空目标数据库中的业务表，因此只在显式设置了环境变量
 * {@code ENROLL_IT=true} 时运行，并且必须指向独立的测试库，
 * 例如 {@code MYSQL_DATABASE=enroll_it}。</p>
 */
@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "enroll.login.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "ENROLL_IT", matches = "true")
class AdmissionFlowIntegrationTest {

    @Autowired
    private IStudentService studentService;

    @Autowired
    private IExcelService excelService;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private StatusMapper statusMapper;

    @BeforeEach
    void cleanDatabase() {
        studentService.reset();
    }

    @Test
    @DisplayName("投档 + 调剂 + 导出全流程的数据一致性")
    void shouldRunWholeAdmissionFlow() throws Exception {
        Department department = new Department();
        department.setDepartmentName("计算机学院");
        departmentMapper.insertDepartment(department);
        assertNotNull(department.getDepartmentId(), "插入学院后应回填自增主键");

        majorMapper.insertMajor(Arrays.asList(
                major("A", department.getDepartmentId(), 2),
                major("B", department.getDepartmentId(), 1),
                major("C", department.getDepartmentId(), 1)));
        statusMapper.addLog("导入专业招生计划文件", EnrollStatus.WITHOUT_STUDENT.ordinal());

        studentMapper.insertStudent(Arrays.asList(
                student(1, 1, "A"),
                student(2, 1, "A"),
                student(3, 1, "A", "B"),
                student(4, 1, "A"),
                student(5, 0, "B")));
        statusMapper.addLog("导入考生志愿文件", EnrollStatus.FILE_READY.ordinal());

        studentService.doEnroll();

        List<ExcelStudent> afterEnroll = studentMapper.getStudentRawForEnroll(0, 100);
        assertEquals(5, afterEnroll.size());
        assertEquals(1, afterEnroll.get(0).getAcceptedType(), "第 1 志愿应命中 A");
        assertEquals("A", afterEnroll.get(0).getAcceptedMajorId());
        assertEquals(1, afterEnroll.get(1).getAcceptedType(), "A 还剩 1 个计划，第 2 名考生应命中 A");
        assertEquals(2, afterEnroll.get(2).getAcceptedType(), "A 已满，第 3 名考生应顺延到第 2 志愿 B");
        assertEquals(EnrollEngine.TYPE_WAIT_ADJUST, afterEnroll.get(3).getAcceptedType(), "服从调剂但志愿落空");
        assertEquals(EnrollEngine.TYPE_EXIT, afterEnroll.get(4).getAcceptedType(), "B 已满且不服从调剂，应退档");

        assertEquals(2, planOf("A").getRealisticStudentCount());
        assertEquals(1, planOf("B").getRealisticStudentCount());
        assertEquals(0, planOf("C").getRealisticStudentCount());

        // 专业 C 没有任何投档记录，也必须进入调剂池，否则会出现"计划没招满却无法调剂"。
        studentService.doAdjust();

        List<ExcelStudent> afterAdjust = studentMapper.getStudentRawForEnroll(0, 100);
        ExcelStudent waiting = afterAdjust.get(3);
        assertEquals(EnrollEngine.TYPE_ADJUSTED, waiting.getAcceptedType(), "服从调剂的考生应被调剂到 C");
        assertEquals("C", waiting.getAcceptedMajorId());
        assertEquals(1, planOf("C").getRealisticStudentCount());
        assertEquals(EnrollEngine.TYPE_EXIT, afterAdjust.get(4).getAcceptedType());

        assertEquals(EnrollStatus.ADJUSTED.ordinal(), statusMapper.getStatus());
        assertEquals(4, queryAdmitted().size(), "A/A/B/C 共 4 人被录取");

        ByteArrayOutputStream admitted = new ByteArrayOutputStream();
        excelService.doExport(admitted);
        assertTrue(admitted.size() > 0, "录取结果 Excel 不应为空");

        ByteArrayOutputStream exited = new ByteArrayOutputStream();
        excelService.exportExitStudent(exited);
        assertTrue(exited.size() > 0, "退档结果 Excel 不应为空");
    }

    @Test
    @DisplayName("重置后所有考生与专业的录取状态回到初始值")
    void resetShouldClearAdmissionState() {
        Department department = new Department();
        department.setDepartmentName("外国语学院");
        departmentMapper.insertDepartment(department);
        majorMapper.insertMajor(Arrays.asList(major("D", department.getDepartmentId(), 1)));
        statusMapper.addLog("导入专业招生计划文件", EnrollStatus.WITHOUT_STUDENT.ordinal());
        studentMapper.insertStudent(Arrays.asList(student(1, 0, "D")));
        statusMapper.addLog("导入考生志愿文件", EnrollStatus.FILE_READY.ordinal());

        studentService.doEnroll();
        assertEquals(1, planOf("D").getRealisticStudentCount());

        studentService.reset();

        assertEquals(EnrollStatus.START.ordinal(), statusMapper.getStatus());
        assertTrue(majorMapper.getMajorPlan().isEmpty(), "重置后招生计划应为空");
        assertTrue(studentMapper.getStudentRaw().isEmpty(), "重置后考生数据应为空");
    }

    private List<StudentResult> queryAdmitted() {
        List<StudentResult> admitted = new ArrayList<>();
        int start = 0;
        while (true) {
            List<StudentResult> page = studentMapper.getStudentForExport(start, 200);
            if (page.isEmpty()) {
                break;
            }
            admitted.addAll(page);
            start += 200;
        }
        return admitted;
    }

    private ExcelMajor planOf(String majorId) {
        return majorMapper.getMajorPlan().stream()
                .filter(major -> majorId.equals(major.getMajorId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("招生计划不存在：" + majorId));
    }

    private static ExcelMajor major(String majorId, int departmentId, int planStudentCount) {
        ExcelMajor major = new ExcelMajor();
        major.setMajorId(majorId);
        major.setMajorCode("CODE_" + majorId);
        major.setMajorName("专业" + majorId);
        major.setDepartmentId(departmentId);
        major.setPeriod(4);
        major.setPlanStudentCount(planStudentCount);
        return major;
    }

    private static ExcelStudent student(int rank, int adjust, String... wills) {
        ExcelStudent student = new ExcelStudent();
        student.setCandidate("2026" + String.format("%04d", rank));
        student.setStudentName("考生" + rank);
        student.setRank(rank);
        student.setTotalGrade(600 - rank);
        student.setProvince("广东");
        student.setCity("广州");
        student.setSubjectType("物理");
        student.setAdjust(adjust);
        student.setWill1(wills.length > 0 ? wills[0] : null);
        student.setWill2(wills.length > 1 ? wills[1] : null);
        student.setWill3(wills.length > 2 ? wills[2] : null);
        student.setWill4(wills.length > 3 ? wills[3] : null);
        student.setWill5(wills.length > 4 ? wills[4] : null);
        student.setWill6(wills.length > 5 ? wills[5] : null);
        return student;
    }
}
