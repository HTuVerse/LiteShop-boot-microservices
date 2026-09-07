package org.hongtu.exception;

import cn.dev33.satoken.exception.*;
import cn.dev33.satoken.util.SaResult;
import org.hongtu.Result.Result;
import org.hongtu.enums.CodeMsg;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Replaced by GlobalExceptionHandler, which also preserves BusinessException codes.
@Deprecated
public class GlobalException{

	// 拦截：未登录异常
	@ExceptionHandler(NotLoginException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public Result handlerException(NotLoginException e) {
		e.printStackTrace();
		// 返回给前端
		return Result.error(CodeMsg.NOT_LOGIN.getCode(),e.getMessage());
	}

	// 拦截：缺少权限异常
	@ExceptionHandler(NotPermissionException.class)
	public Result handlerException(NotPermissionException e) {
		e.printStackTrace();
		return Result.error(CodeMsg.FORBIDDEN.getCode(),"缺少权限：" + e.getPermission());
	}

	// 拦截：缺少角色异常
	@ExceptionHandler(NotRoleException.class)
	public Result handlerException(NotRoleException e) {
		e.printStackTrace();
		return Result.error(CodeMsg.FORBIDDEN.getCode(),"缺少角色：" + e.getRole());
	}

	// 拦截：二级认证校验失败异常
	@ExceptionHandler(NotSafeException.class)
	public Result handlerException(NotSafeException e) {
		e.printStackTrace();
		return Result.error(CodeMsg.FORBIDDEN.getCode(),"二级认证校验失败：" + e.getService());
	}

	// 拦截：服务封禁异常
	@ExceptionHandler(DisableServiceException.class)
	public Result handlerException(DisableServiceException e) {
		e.printStackTrace();
		return Result.error(CodeMsg.FORBIDDEN.getCode(),"当前账号 " + e.getService() + " 服务已被封禁 (level=" + e.getLevel() + ")：" + e.getDisableTime() + "秒后解封");
	}

	// 拦截：其它所有异常
	@ExceptionHandler(Exception.class)
	public SaResult handlerException(Exception e) {
		e.printStackTrace(); 
		return SaResult.error("未知错误" +e.getMessage());
	}
}
