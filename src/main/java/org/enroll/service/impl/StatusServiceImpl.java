package org.enroll.service.impl;

import org.enroll.mapper.StatusMapper;
import org.enroll.pojo.EnrollStatus;
import org.enroll.pojo.Log;
import org.enroll.service.interfaces.IStatusService;
import org.enroll.service.interfaces.IStudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class StatusServiceImpl implements IStatusService {

    @Autowired
    private StatusMapper statusMapper;

    @Autowired
    private IStudentService studentService;


    @Override
    public Integer getStatus() {
        return statusMapper.getStatus();
    }


    @Override
    public List<Log> getLogList(){
        return statusMapper.getLogList();
    }


    @Override
    public void reset() {
        // 重置逻辑统一放在 StudentServiceImpl，避免两处实现产生行为差异。
        studentService.reset();
    }
}
