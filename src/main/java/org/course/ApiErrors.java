package org.course;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestControllerAdvice(basePackages="org.course")
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getReason()==null?"操作失败":e.getReason()));}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.multipart.MaxUploadSizeExceededException.class}) ResponseEntity<?> input(Exception e){return ResponseEntity.badRequest().body(Map.of("message","请求格式无效或文件超过 2 MB"));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e){org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("Request failed: {}",e.getClass().getSimpleName());return ResponseEntity.status(500).body(Map.of("message","服务暂时无法完成操作，数据未提交，请稍后重试"));}
}
