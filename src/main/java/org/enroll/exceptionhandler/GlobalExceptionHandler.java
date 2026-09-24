package org.enroll.exceptionhandler;

import lombok.extern.slf4j.Slf4j;
import org.enroll.exception.ReadExcelException;
import org.enroll.utils.JsonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 统一异常处理：把异常转换成前端约定的 {@link JsonResponse} 结构。
 *
 * <p>业务状态不满足时返回 001，提示可以直接展示给使用者；
 * 其他未预期异常记录到日志，只向调用方返回简要信息，避免暴露堆栈细节。</p>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ResponseBody
    @ExceptionHandler({ReadExcelException.class})
    public JsonResponse handleExcelException(ReadExcelException e) {
        log.warn("导入 Excel 失败：{}", e.getMessage());
        return new JsonResponse(JsonResponse.INVALID_REQUEST, null, "导入Excel失败，请检查文件格式");
    }

    @ResponseBody
    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public JsonResponse handleBusinessException(RuntimeException e) {
        log.warn("业务流程校验未通过：{}", e.getMessage());
        return new JsonResponse(JsonResponse.INVALID_REQUEST, null, e.getMessage());
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public JsonResponse handle(Exception e) {
        log.error("系统异常", e);
        return new JsonResponse(JsonResponse.SYSTEM_ERROR, null, "服务器处理失败，请稍后重试");
    }
}
