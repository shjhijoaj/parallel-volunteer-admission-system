package org.enroll.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.enroll.excel.pojo.ExcelMajor;
import org.enroll.excel.pojo.ExcelStudent;
import org.enroll.mapper.DepartmentMapper;
import org.enroll.mapper.MajorMapper;
import org.enroll.mapper.StatusMapper;
import org.enroll.mapper.StudentMapper;
import org.enroll.pojo.EnrollStatus;
import org.enroll.pojo.StatisticsResult;
import org.enroll.pojo.StudentResult;
import org.enroll.service.EnrollEngine;
import org.enroll.service.interfaces.IStudentService;
import org.enroll.utils.QueryResultOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRED)
public class StudentServiceImpl implements IStudentService {

    /** 分页读取考生与专业计划的批大小，避免一次性把全量数据放入内存。 */
    private static final int BATCH_SIZE = 200;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private StatusMapper statusMapper;


    @Override
    public PageInfo getStudentRaw(int currentPage) {
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.getStudentRaw());
    }

    @Override
    public PageInfo getAdjustStudentRaw(int currentPage){
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.getAdjustStudentRaw());
    }

    @Override
    public PageInfo getExitStudentRaw(int currentPage){
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.getExitStudentRaw());
    }

    @Override
    public void doEnroll() {
        Integer status = statusMapper.getStatus();
        if (status == null || status != EnrollStatus.FILE_READY.ordinal()){
            throw new IllegalStateException("当前状态不能执行录取，请先导入招生计划与考生志愿文件");
        }
        List<ExcelMajor> majors = majorMapper.getMajorPlanForEnroll();
        Map<String, ExcelMajor> planById = EnrollEngine.indexPlanById(majors);

        // 考生按排位（分数）升序分页读取，避免一次性把全量考生载入内存。
        int current = 0;
        int size = BATCH_SIZE;
        int admitted = 0;
        while (true) {
            List<ExcelStudent> students = studentMapper.getStudentRawForEnroll(current, size);
            if (students.isEmpty()) {
                break;
            }
            for (ExcelStudent student : students) {
                EnrollEngine.Match match = EnrollEngine.enroll(student, planById);
                EnrollEngine.apply(student, match);
                if (match.isAdmitted()) {
                    admitted++;
                }
            }
            studentMapper.updateAccepted(students);
            current += size;
        }
        majorMapper.updateStudentCount(majors);
        statusMapper.addLog("录取完成", EnrollStatus.ENROLLED.ordinal());
        log.info("平行志愿投档完成，共录取 {} 人", admitted);
    }

    @Override
    public void doAdjust(){
        Integer status = statusMapper.getStatus();
        if (status == null || status != EnrollStatus.ENROLLED.ordinal()){
            throw new IllegalStateException("当前状态不能执行调剂，请先完成录取");
        }
        List<ExcelMajor> majors = majorMapper.getMajorPlanForAdjust();
        int start = 0, size = BATCH_SIZE, adjusted = 0;
        while (true) {
            List<ExcelStudent> students = studentMapper.getStudentRawForAdjust(start, size);
            if (students.isEmpty()) {
                break;
            }
            adjusted += EnrollEngine.adjust(students, majors);
            // 被调剂的考生 accepted_type 由 0 变为 7，记录会从结果集中消失，
            // 因此这里不能推进 start，否则会漏掉后面排位的考生。
            studentMapper.updateAdjust(students);
        }
        majorMapper.updateStudentCount(majors);
        statusMapper.addLog("调剂完成", EnrollStatus.ADJUSTED.ordinal());
        log.info("专业调剂完成，共调剂 {} 人", adjusted);
    }



    @Override
    public PageInfo getResult(int currentPage, boolean desc, QueryResultOption option) {
        PageHelper.startPage(currentPage,50);
        return new PageInfo<>(studentMapper.getStudent(desc, option));
    }

    @Override
    public PageInfo getResultByDepartment(int departmentId, int currentPage, boolean desc) {
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.getStudentByDepartment(departmentId, desc));
    }

    @Override
    public PageInfo getResultByMajor(String majorId, int currentPage, boolean desc) {
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.getStudentByMajor(majorId, desc));
    }

    @Override
    public PageInfo searchStudent(int currentPage, String keyword){
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.searchStudent(keyword));
    }

    @Override
    public PageInfo searchStudentByCandidate(int currentPage, String keyword){
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.searchStudentByCandidate(keyword));
    }

    @Override
    public PageInfo getStudentBeforeRank(int currentPage, int rank){
        PageHelper.startPage(currentPage, 50);
        return new PageInfo<>(studentMapper.getStudentBeforeRank(rank));
    }

    @Override
    public List<StatisticsResult> getStatisticsResult(){
        return studentMapper.getStatisticsResult();
    }

    @Override
    public List<StatisticsResult> getStatisticsResultInDepartment() {
        return studentMapper.getStatisticsResultInDepartment();
    }

    @Override
    public List<StatisticsResult> getStatisticsResultInMajor() {
        List<StatisticsResult> result = studentMapper.getStatisticsResultInMajor();
        return result;
    }

    @Override
    public List<Map<String, Object>> getDistribute() {
        return studentMapper.getDistribute();
    }

    @Override
    public List<Map<String, Object>> getDistributeInProvince(String province) {
        return studentMapper.getDistributeInProvince(province);
    }

    @Override
    public List<Map<String, Object>> getGradeDistribute() {
        return studentMapper.getGradeDistribute();
    }

    @Override
    public List<Map<String, Object>> getGradeDistributeByDepartment(int departmentId) {
        return studentMapper.getGradeDistributeByDepartment(departmentId);
    }

    @Override
    public List<Map<String, Object>> getGradeDistributeByMajor(String majorId) {
        return studentMapper.getGradeDistributeByMajor(majorId);
    }

    @Override
    public List<Map<String, Object>> getCountDistributeInDepartment() {
        return studentMapper.getCountDistributeInDepartment();
    }

    @Override
    public List<Map<String, Object>> getCountDistributeInMajor() {
        return studentMapper.getCountDistributeInMajor();
    }

    @Override
    public List<Map<String, Object>> getCountDistributeInMajorByDepartment(int departmentId) {
        return studentMapper.getCountDistributeInMajorByDepartment(departmentId);
    }

    @Override
    @Deprecated
    public void reset(){
        studentMapper.resetTable();
        majorMapper.resetTable();
        departmentMapper.resetTable();
        statusMapper.addLog("重置系统", EnrollStatus.START.ordinal());
        log.warn("系统数据已重置：考生、专业与学院表已被清空");
    }

    /**
     * 早期前端流程中的"准备录取"步骤。
     *
     * <p>当前版本在导入考生志愿后状态即为 {@link EnrollStatus#FILE_READY}，
     * 因此该步骤不再需要切换状态，保留接口是为了兼容已发布的 Vue 页面。</p>
     */
    @Override
    @Deprecated
    public void formallyReady(){
        log.info("formalReady 已被录取流程废弃，导入完成后可直接执行录取");
    }
}
