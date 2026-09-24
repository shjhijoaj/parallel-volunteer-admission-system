package org.enroll.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.enroll.excel.pojo.ExcelStudent;
import org.enroll.exception.ReadExcelException;
import org.enroll.mapper.StudentMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ReadStudentListener extends AnalysisEventListener<ExcelStudent> {
    // 这里必须是实例字段：EasyExcel 每次导入都会 new 一个监听器，
    // 写成 static 会让多个导入请求共享同一份缓存，出现数据串号。
    private final List<ExcelStudent> list = new ArrayList<>();
    private static final int BATCH_COUNT = 20;

    private final StudentMapper studentMapper;

    public ReadStudentListener(StudentMapper studentMapper){
        this.studentMapper = studentMapper;
    }


    @Override
    public void invoke(ExcelStudent excelStudent, AnalysisContext analysisContext) {
        list.add(excelStudent);
        if (list.size() >= BATCH_COUNT) {
            this.save();
            list.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        this.save();
        list.clear();
    }

    private void save(){
        if (list.size() > 0) {
            if (StringUtils.isEmpty(list.get(0).getStudentName())) {
                list.clear();
                throw new ReadExcelException("导入Excel失败，请检查文件格式");
            }
            this.studentMapper.insertStudent(list);
        }

    }

    @Override
    public void onException(Exception exception, AnalysisContext context){
        throw new ReadExcelException("导入Excel失败，请检查文件格式");
    }


}
