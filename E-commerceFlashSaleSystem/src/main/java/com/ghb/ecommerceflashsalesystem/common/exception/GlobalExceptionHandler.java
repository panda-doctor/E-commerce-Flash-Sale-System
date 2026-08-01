package com.ghb.ecommerceflashsalesystem.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler（处理器类）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  /**
   * 业务异常 —— 直接返回对应的业务状态码
   */
  @ExceptionHandler(BusinessException.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> handleBusinessException(BusinessException e){
    log.warn("业务异常 | code={}, message={}", e.getCode(), e.getMessage());
    return Result.fail(e.getResultCode(), e.getMessage());
  }

  /**
   * 2. @Valid 校验请求体失败 —— MethodArgumentNotValidException
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
    String errorMsg = e.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.joining("; "));
    log.warn("请求参数校验失败（请求体）: {}", errorMsg);
    return Result.fail(ResultCode.PARAM_ERROR, errorMsg);
  }
  /**
   * 3. @RequestParam / @PathVariable 校验失败 —— ConstraintViolationException
   */
  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
    String errorMsg = e.getConstraintViolations().stream()
            .map(ConstraintViolation ::getMessage)
            .collect(Collectors.joining("; "));
    log.warn("请求参数校验失败（参数/路径）: {}", errorMsg);
    return Result.fail(ResultCode.PARAM_ERROR, errorMsg);
  }
  /**
   * 4. 请求体 JSON 解析失败 —— HttpMessageNotReadableException
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
    // 对于常见的 InvalidFormatException（类型转换异常），可提取更明确的提示
    String errorMsg = "请求体格式错误";
    if (e.getCause() instanceof InvalidFormatException) {
      InvalidFormatException ife = (InvalidFormatException) e.getCause();
      // 可以构造更友好的提示，例如 "字段 xxx 类型错误，期望类型为 xxx"
      errorMsg = "请求参数类型或格式错误";
    }
    log.warn("请求体解析失败: {}", e.getMessage());
    return Result.fail(ResultCode.PARAM_ERROR, errorMsg);
  }
  /**
   * 5. 兜底异常 —— 所有未捕获的异常，统一返回系统错误
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> handleException(Exception e) {
    // 必须记录完整堆栈，方便排查
    log.error("系统异常", e);
    return Result.fail(ResultCode.SYSTEM_ERROR);
  }
}
