package org.enroll.service;

import org.enroll.excel.pojo.ExcelMajor;
import org.enroll.excel.pojo.ExcelStudent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平行志愿投档与调剂的规则测试。
 *
 * <p>这些用例不启动 Spring、不连接数据库，直接校验"分数优先、遵循志愿"的核心规则，
 * 因此可以在任何环境下秒级运行。</p>
 */
class EnrollEngineTest {

    @Test
    @DisplayName("第一志愿有计划余额时直接投档到第一志愿")
    void shouldAdmitToFirstWillWhenPlanAvailable() {
        ExcelStudent student = student(1, 1, "A", "B");
        Map<String, ExcelMajor> plan = EnrollEngine.indexPlanById(Arrays.asList(major("A", 2), major("B", 2)));

        EnrollEngine.Match match = EnrollEngine.enroll(student, plan);

        assertEquals("A", match.getMajorId());
        assertEquals(EnrollEngine.TYPE_FIRST_WILL, match.getAcceptedType());
        assertEquals(1, plan.get("A").getRealisticStudentCount());
        assertTrue(match.isAdmitted());
    }

    @Test
    @DisplayName("前面的志愿已录满时顺延到下一个仍有缺额的志愿")
    void shouldSkipFullWillAndAdmitToNext() {
        ExcelStudent student = student(1, 1, "A", "B", "C");
        ExcelMajor majorA = major("A", 1);
        majorA.setRealisticStudentCount(1);
        ExcelMajor majorB = major("B", 1);
        majorB.setRealisticStudentCount(1);
        Map<String, ExcelMajor> plan = EnrollEngine.indexPlanById(Arrays.asList(majorA, majorB, major("C", 3)));

        EnrollEngine.Match match = EnrollEngine.enroll(student, plan);

        assertEquals("C", match.getMajorId());
        assertEquals(EnrollEngine.TYPE_THIRD_WILL, match.getAcceptedType());
        assertEquals(1, plan.get("C").getRealisticStudentCount());
    }

    @Test
    @DisplayName("空白志愿和未在招生计划中的专业代号会被跳过")
    void shouldIgnoreBlankAndUnknownWills() {
        ExcelStudent student = student(1, 1, "  ", "NOT_EXIST", null, "D");
        Map<String, ExcelMajor> plan = EnrollEngine.indexPlanById(Arrays.asList(major("A", 1), major("D", 1)));

        EnrollEngine.Match match = EnrollEngine.enroll(student, plan);

        assertEquals("D", match.getMajorId());
        assertEquals(EnrollEngine.TYPE_FOURTH_WILL, match.getAcceptedType());
    }

    @Test
    @DisplayName("全部志愿落空且服从调剂时进入调剂队列")
    void shouldWaitForAdjustWhenAllWillsFullAndAdjustAccepted() {
        ExcelStudent student = student(1, 1, "A");
        ExcelMajor majorA = major("A", 1);
        majorA.setRealisticStudentCount(1);

        EnrollEngine.Match match = EnrollEngine.enroll(student, EnrollEngine.indexPlanById(Arrays.asList(majorA)));

        assertNull(match.getMajorId());
        assertEquals(EnrollEngine.TYPE_WAIT_ADJUST, match.getAcceptedType());
        assertFalse(match.isAdmitted());
    }

    @Test
    @DisplayName("全部志愿落空且不服从调剂时直接退档")
    void shouldExitWhenAllWillsFullAndRefuseAdjust() {
        ExcelStudent student = student(1, 0, "A");
        ExcelMajor majorA = major("A", 1);
        majorA.setRealisticStudentCount(1);

        EnrollEngine.Match match = EnrollEngine.enroll(student, EnrollEngine.indexPlanById(Arrays.asList(majorA)));

        assertEquals(EnrollEngine.TYPE_EXIT, match.getAcceptedType());
    }

    @Test
    @DisplayName("专业实际录取人数不会超过招生计划数")
    void shouldNotExceedPlanCount() {
        ExcelMajor majorA = major("A", 2);
        Map<String, ExcelMajor> plan = EnrollEngine.indexPlanById(Arrays.asList(majorA));

        EnrollEngine.Match first = EnrollEngine.enroll(student(1, 0, "A"), plan);
        EnrollEngine.Match second = EnrollEngine.enroll(student(2, 0, "A"), plan);
        EnrollEngine.Match third = EnrollEngine.enroll(student(3, 0, "A"), plan);

        assertTrue(first.isAdmitted());
        assertTrue(second.isAdmitted());
        assertEquals(EnrollEngine.TYPE_EXIT, third.getAcceptedType());
        assertEquals(2, majorA.getRealisticStudentCount());
    }

    @Test
    @DisplayName("调剂按缺额专业顺序依次补录，缺额用完后剩余考生退档")
    void adjustShouldFillVacanciesInOrderAndExitRest() {
        ExcelMajor majorA = major("A", 2);
        ExcelMajor majorB = major("B", 1);
        List<ExcelStudent> waitings = new ArrayList<>(Arrays.asList(
                student(1, 1, "X"), student(2, 1, "X"), student(3, 1, "X"), student(4, 1, "X")));

        int adjusted = EnrollEngine.adjust(waitings, Arrays.asList(majorA, majorB));

        assertEquals(3, adjusted);
        assertEquals("A", waitings.get(0).getAcceptedMajorId());
        assertEquals("A", waitings.get(1).getAcceptedMajorId());
        assertEquals("B", waitings.get(2).getAcceptedMajorId());
        assertEquals(EnrollEngine.TYPE_ADJUSTED, waitings.get(2).getAcceptedType());
        assertEquals(EnrollEngine.TYPE_EXIT, waitings.get(3).getAcceptedType());
        assertEquals(2, majorA.getRealisticStudentCount());
        assertEquals(1, majorB.getRealisticStudentCount());
    }

    @Test
    @DisplayName("调剂时跳过已经录满的专业")
    void adjustShouldSkipFullMajors() {
        ExcelMajor full = major("A", 1);
        full.setRealisticStudentCount(1);
        ExcelMajor vacancy = major("B", 2);
        List<ExcelStudent> waitings = new ArrayList<>(Arrays.asList(student(1, 1, "X"), student(2, 1, "X")));

        int adjusted = EnrollEngine.adjust(waitings, Arrays.asList(full, vacancy));

        assertEquals(2, adjusted);
        assertEquals("B", waitings.get(0).getAcceptedMajorId());
        assertEquals("B", waitings.get(1).getAcceptedMajorId());
    }

    private static ExcelMajor major(String majorId, int planStudentCount) {
        ExcelMajor major = new ExcelMajor();
        major.setMajorId(majorId);
        major.setMajorName("专业" + majorId);
        major.setPlanStudentCount(planStudentCount);
        major.setRealisticStudentCount(0);
        return major;
    }

    private static ExcelStudent student(int rank, int adjust, String... wills) {
        ExcelStudent student = new ExcelStudent();
        student.setStudentName("考生" + rank);
        student.setCandidate("C" + rank);
        student.setRank(rank);
        student.setTotalGrade(600 - rank);
        student.setAdjust(adjust);
        student.setAcceptedType(EnrollEngine.TYPE_UNSET);
        String[] all = new String[EnrollEngine.MAX_WILL_COUNT];
        System.arraycopy(wills, 0, all, 0, Math.min(wills.length, all.length));
        student.setWill1(all[0]);
        student.setWill2(all[1]);
        student.setWill3(all[2]);
        student.setWill4(all[3]);
        student.setWill5(all[4]);
        student.setWill6(all[5]);
        return student;
    }
}
