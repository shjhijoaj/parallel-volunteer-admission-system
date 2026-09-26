package org.enroll.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.enroll.excel.listener.ReadMajorListener;
import org.enroll.excel.listener.ReadStudentListener;
import org.enroll.excel.pojo.ExcelMajor;
import org.enroll.excel.pojo.ExcelStudent;
import org.enroll.mapper.DepartmentMapper;
import org.enroll.mapper.MajorMapper;
import org.enroll.mapper.StatusMapper;
import org.enroll.mapper.StudentMapper;
import org.enroll.pojo.EnrollStatus;
import org.enroll.pojo.StudentResult;
import org.enroll.service.interfaces.IExcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRED)
public class ExcelServiceImpl implements IExcelService {

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private StatusMapper statusMapper;

    public void ReadMajorExcel(MultipartFile file) throws IOException {
        Integer status = statusMapper.getStatus();
        requireStatus(status, EnrollStatus.START, "招生计划");
        validateExcelUpload(file, "招生计划");
        // 先完成状态和文件校验，再清理旧数据，避免非法重复导入误删当前数据。
        majorMapper.resetTable();
        studentMapper.resetStudent();
        EasyExcel.read(file.getInputStream(), ExcelMajor.class, new ReadMajorListener(majorMapper, departmentMapper)).sheet().doRead();
        statusMapper.addLog("导入专业招生计划文件", EnrollStatus.WITHOUT_STUDENT.ordinal());
    }

    public void ReadStudentExcel(MultipartFile file) throws IOException {
        Integer status = statusMapper.getStatus();
        requireStatus(status, EnrollStatus.WITHOUT_STUDENT, "考生志愿");
        validateExcelUpload(file, "考生志愿");
        studentMapper.resetTable();
        majorMapper.resetMajor();
        EasyExcel.read(file.getInputStream(), ExcelStudent.class,new ReadStudentListener(studentMapper)).sheet().doRead();
        statusMapper.addLog("导入考生志愿文件", EnrollStatus.FILE_READY.ordinal());
    }

    private void requireStatus(Integer status, EnrollStatus expected, String fileType) {
        if (status == null || status != expected.ordinal()) {
            throw new IllegalStateException("当前流程状态不能导入" + fileType + "文件");
        }
    }

    private void validateExcelUpload(MultipartFile file, String fileType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fileType + "文件不能为空");
        }
        String filename = file.getOriginalFilename();
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new IllegalArgumentException(fileType + "文件必须是 .xlsx 或 .xls 格式");
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            throw new IllegalArgumentException(fileType + "文件不能超过 10 MB");
        }
    }

    @Override
    public void doExport(OutputStream os){
        int start = 0, size = 200;
        ExcelWriter excelWriter = null;
        Integer status = statusMapper.getStatus();
        if (status != EnrollStatus.ADJUSTED.ordinal()){
            throw new RuntimeException("未结束流程不能导出结果");
        }
        try {
            excelWriter = EasyExcel.write(os, StudentResult.class).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("录取结果").build();
            while(true) {
                List<StudentResult> results = studentMapper.getStudentForExport(start,size);
                if(results.size() == 0)
                    break;
                excelWriter.write(results, writeSheet);
                start += size;
            }
        } finally {
            if (excelWriter != null) {
                excelWriter.finish();
            }
        }
    }

    @Override
    public void exportExitStudent(OutputStream os) {
        int start = 0, size = 200;
        ExcelWriter excelWriter = null;
        Integer status = statusMapper.getStatus();
        if (status != EnrollStatus.ADJUSTED.ordinal()){
            throw new RuntimeException("未结束流程不能导出结果");
        }
        try {
            excelWriter = EasyExcel.write(os, ExcelStudent.class).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("退档结果").build();
            while(true) {
                List<ExcelStudent> results = studentMapper.getExitStudentForExport(start,size);
                if(results.size() == 0)
                    break;
                excelWriter.write(results, writeSheet);
                start += size;
            }
        } finally {
            if (excelWriter != null) {
                excelWriter.finish();
            }
        }
    }
}
