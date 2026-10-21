package org.hongtu.exception;

import cn.dev33.satoken.exception.*;
import org.hongtu.Result.Result;
import org.hongtu.enums.CodeMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * @author Honghong
 * @since 2025-07-31
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // 业务规则拒绝属于预期结果，响应仍保留原错误码和提示。
        logger.debug("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(TenantContextMissingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTenantContextMissingException(TenantContextMissingException e) {
        logger.warn("缺少租户上下文: {}", e.getMessage());
        return Result.error(CodeMsg.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        return Result.error(CodeMsg.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 订单等业务规则使用 IllegalStateException 表达可展示原因。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalStateException(IllegalStateException e) {
        logger.warn("业务状态异常: {}", e.getMessage());
        return Result.error(CodeMsg.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleSecurityException(SecurityException e) {
        logger.warn("访问被拒绝: {}", e.getMessage());
        return Result.error(CodeMsg.FORBIDDEN.getCode(), e.getMessage());
    }
    
    /**
     * 处理参数校验异常 (MethodArgumentNotValidException)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        String errorMessage = fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        logger.error("参数校验异常: {}", errorMessage);
        return Result.error(CodeMsg.PARAM_ERROR.getCode(), errorMessage);
    }
    
    /**
     * 处理参数绑定异常 (BindException)
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        String errorMessage = fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        logger.error("参数绑定异常: {}", errorMessage);
        return Result.error(CodeMsg.PARAM_ERROR.getCode(), errorMessage);
    }

    
    /**
     * 处理参数类型不匹配异常 (MethodArgumentTypeMismatchException)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logger.error("参数类型不匹配: {}", e.getMessage());
        return Result.error(CodeMsg.PARAM_ERROR.getCode(), "参数类型不匹配: " + e.getName() + " 应为 " + e.getRequiredType().getSimpleName() + " 类型");
    }
    
    /**
     * 处理缺少请求参数异常 (MissingServletRequestParameterException)
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logger.error("缺少请求参数: {}", e.getMessage());
        return Result.error(CodeMsg.PARAM_ERROR.getCode(), "缺少请求参数: " + e.getParameterName());
    }
    
    /**
     * 处理请求方法不支持异常 (HttpRequestMethodNotSupportedException)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        logger.error("请求方法不支持: {}", e.getMessage());
        return Result.error(CodeMsg.METHOD_NOT_ALLOWED.getCode(), "请求方法不支持: " + e.getMethod());
    }
    
    /**
     * 处理文件上传大小超限异常 (MaxUploadSizeExceededException)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logger.error("上传文件大小超过限制: {}", e.getMessage());
        return Result.error(CodeMsg.UPLOAD_SIZE_EXCEEDED.getCode(), "上传文件大小超过限制");
    }
    
    /**
     * 处理数据库异常 (SQLException)
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleSQLException(SQLException e) {
        logger.error("数据库异常: {}", e.getMessage(), e);
        return Result.error(CodeMsg.DB_ERROR.getCode(), "数据库操作异常");
    }

    // ===== Sa-Token异常处理 =====
    
    /**
     * 处理未登录异常 (NotLoginException)
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        logger.error("未登录异常: {}", e.getMessage());
        return Result.error(CodeMsg.NOT_LOGIN.getCode(), e.getMessage());
    }
    
    /**
     * 处理权限不足异常 (NotPermissionException)
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        logger.error("权限不足异常: {}", e.getMessage());
        return Result.error(CodeMsg.FORBIDDEN.getCode(), "缺少权限: " + e.getPermission());
    }
    
    /**
     * 处理角色不足异常 (NotRoleException)
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        logger.error("角色不足异常: {}", e.getMessage());
        return Result.error(CodeMsg.FORBIDDEN.getCode(), "缺少角色: " + e.getRole());
    }
    
    /**
     * 处理二级认证校验失败异常 (NotSafeException)
     */
    @ExceptionHandler(NotSafeException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotSafeException(NotSafeException e) {
        logger.error("二级认证校验失败异常: {}", e.getMessage());
        return Result.error(CodeMsg.FORBIDDEN.getCode(), "二级认证校验失败: " + e.getService());
    }
    
    /**
     * 处理服务封禁异常 (DisableServiceException)
     */
    @ExceptionHandler(DisableServiceException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleDisableServiceException(DisableServiceException e) {
        logger.error("服务封禁异常: {}", e.getMessage());
        return Result.error(CodeMsg.FORBIDDEN.getCode(), 
                "当前账号 " + e.getService() + " 服务已被封禁 (level=" + e.getLevel() + "): " + e.getDisableTime() + "秒后解封");
    }
    
    /**
     * 处理所有其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        String ref = UUID.randomUUID().toString().substring(0, 8);
        logger.error("系统异常 ref={}", ref, e);
        return Result.error(CodeMsg.SERVER_ERROR.getCode(), "系统异常，请联系管理员（" + ref + "）");
    }
} 
