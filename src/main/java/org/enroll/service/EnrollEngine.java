package org.enroll.service;

import org.enroll.excel.pojo.ExcelMajor;
import org.enroll.excel.pojo.ExcelStudent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平行志愿投档与专业调剂的核心规则引擎。
 *
 * <p>引擎只做分数优先、遵循志愿的纯计算，不依赖 Spring 容器和数据库，
 * 因此可以直接用单元测试覆盖"志愿命中、计划录满、服从调剂、退档"等分支。
 * 数据库读写仍由 {@code StudentMapper} 负责，服务层只负责编排两者。</p>
 */
public final class EnrollEngine {

    /** 尚未开始录取。 */
    public static final int TYPE_UNSET = -2;
    /** 投档成功，取值 1-6 表示被第几志愿录取。 */
    public static final int TYPE_FIRST_WILL = 1;
    public static final int TYPE_SECOND_WILL = 2;
    public static final int TYPE_THIRD_WILL = 3;
    public static final int TYPE_FOURTH_WILL = 4;
    public static final int TYPE_FIFTH_WILL = 5;
    public static final int TYPE_SIXTH_WILL = 6;
    /** 全部志愿未命中且服从调剂，等待调剂。 */
    public static final int TYPE_WAIT_ADJUST = 0;
    /** 调剂环节录取。 */
    public static final int TYPE_ADJUSTED = 7;
    /** 全部志愿未命中且不服从调剂，退档。 */
    public static final int TYPE_EXIT = -1;

    /** 一位考生最多填报 6 个院校专业志愿。 */
    public static final int MAX_WILL_COUNT = 6;

    private EnrollEngine() {
    }

    /**
     * 按填报顺序取出 6 个志愿代号，缺失的位置保持 null。
     */
    public static List<String> willsOf(ExcelStudent student) {
        return Arrays.asList(
                student.getWill1(), student.getWill2(), student.getWill3(),
                student.getWill4(), student.getWill5(), student.getWill6());
    }

    /**
     * 为单个考生投档：从第 1 志愿开始依次检索，命中第一个"计划未满"的专业即投档。
     *
     * <p>命中时会直接把该专业的已录人数加一，保证调用方不必再维护计数。
     * 若 6 个志愿都未命中，则按是否服从调剂返回 {@link #TYPE_WAIT_ADJUST} 或 {@link #TYPE_EXIT}。</p>
     *
     * @param student  考生
     * @param planById 专业代号 -> 招生计划（含动态变化的已录人数）
     * @return 投档结果
     */
    public static Match enroll(ExcelStudent student, Map<String, ExcelMajor> planById) {
        List<String> wills = willsOf(student);
        for (int index = 0; index < wills.size(); index++) {
            String majorId = trimToNull(wills.get(index));
            if (majorId == null) {
                continue;
            }
            ExcelMajor major = planById.get(majorId);
            if (major == null) {
                continue;
            }
            if (hasVacancy(major)) {
                major.setRealisticStudentCount(major.getRealisticStudentCount() + 1);
                return new Match(majorId, index + 1);
            }
        }
        if (student.getAdjust() == 1) {
            return new Match(null, TYPE_WAIT_ADJUST);
        }
        return new Match(null, TYPE_EXIT);
    }

    /**
     * 专业调剂：把"服从调剂且尚未录取"的考生按排位顺序，依次补进仍有缺额的专业。
     *
     * <p>缺额专业顺序由调用方决定（本系统按专业最低排位排序），当所有缺额用完后，
     * 剩余考生统一置为退档。</p>
     *
     * @param waitings  待调剂考生，按排位升序
     * @param vacancies 仍有缺额的专业，按投档优先级排列
     * @return 实际完成调剂的考生人数
     */
    public static int adjust(List<ExcelStudent> waitings, List<ExcelMajor> vacancies) {
        int adjusted = 0;
        int index = 0;
        for (ExcelStudent student : waitings) {
            while (index < vacancies.size() && !hasVacancy(vacancies.get(index))) {
                index++;
            }
            if (index >= vacancies.size()) {
                student.setAcceptedMajorId(null);
                student.setAcceptedType(TYPE_EXIT);
                continue;
            }
            ExcelMajor major = vacancies.get(index);
            major.setRealisticStudentCount(major.getRealisticStudentCount() + 1);
            student.setAcceptedMajorId(major.getMajorId());
            student.setAcceptedType(TYPE_ADJUSTED);
            adjusted++;
        }
        return adjusted;
    }

    /**
     * 把投档结果写回考生对象，保持"引擎只算、对象只存"的职责划分。
     */
    public static void apply(ExcelStudent student, Match match) {
        student.setAcceptedMajorId(match.getMajorId());
        student.setAcceptedType(match.getAcceptedType());
    }

    /** 专业是否仍有招生计划余额。 */
    public static boolean hasVacancy(ExcelMajor major) {
        return major != null && major.getRealisticStudentCount() < major.getPlanStudentCount();
    }

    /**
     * 收集专业缺额表，便于投档阶段 O(1) 命中。
     */
    public static Map<String, ExcelMajor> indexPlanById(List<ExcelMajor> plan) {
        Map<String, ExcelMajor> map = new HashMap<>(plan.size() * 2);
        for (ExcelMajor major : plan) {
            map.put(major.getMajorId(), major);
        }
        return map;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 单次投档结果。
     */
    public static final class Match {

        private final String majorId;
        private final int acceptedType;

        public Match(String majorId, int acceptedType) {
            this.majorId = majorId;
            this.acceptedType = acceptedType;
        }

        public String getMajorId() {
            return majorId;
        }

        public int getAcceptedType() {
            return acceptedType;
        }

        public boolean isAdmitted() {
            return acceptedType >= TYPE_FIRST_WILL && acceptedType <= TYPE_ADJUSTED;
        }

        @Override
        public String toString() {
            return "Match{majorId='" + majorId + "', acceptedType=" + acceptedType + '}';
        }
    }
}
