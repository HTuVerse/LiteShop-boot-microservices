package org.hongtu.Result;

import lombok.Data;
import org.hongtu.enums.CodeMsg;

/**
 * 功能：封装结果类
 * 作者：ht
 * 时间：2025/5/6 上午9:42
 */
@Data
public class Result<T> {
    // 状态码常量
    public static final Integer CODE_SUCCESS = 200;
    public static final Integer CODE_FAIL = 500;
    public static final Integer CODE_UNAUTHORIZED = 401;
    public static final Integer CODE_FORBIDDEN = 403;
    public static final Integer CODE_NOT_FOUND = 404;

    private Integer code;
    private String msg;
    private T data;

    public Result() {
        this.code = CODE_SUCCESS;
        this.msg="请求成功";
    }

    public Result(Integer code, String message) {
        this.code = code;
        this.msg = message;
    }

    public Result( String message,T data) {
        this.code = CODE_SUCCESS;
        this.msg = message;
        this.data=data;
    }

    public Result(T data) {
        this.code = CODE_SUCCESS;
        this.data = data;
    }

    public Result(String message) {
        this.code = CODE_FAIL;
        this.msg = message;
    }

    /**
     * 使用 CodeMsg 枚举构造结果
     */
    public Result(CodeMsg codeMsg) {
        if (codeMsg != null) {
            this.code = codeMsg.getCode();
            this.msg = codeMsg.getMsg();
        } else {
            this.code = CODE_FAIL;
            this.msg = "未知错误";
        }
    }

    /**
     * 使用 CodeMsg 枚举和数据构造结果
     */
    public Result(CodeMsg codeMsg, T data) {
        if (codeMsg != null) {
            this.code = codeMsg.getCode();
            this.msg = codeMsg.getMsg();
        } else {
            this.code = CODE_SUCCESS;
            this.msg = "请求成功";
        }
        this.data = data;
    }

    public static <T> Result<T> success() {return new Result();}
    public static <T> Result<T> success(T data) {return new Result(data);}
    public static <T> Result<T> success(Integer code,String message) {return new Result(code,message);}
    public static <T> Result<T> success(String message) {
        return new Result(CODE_SUCCESS,message);
    }
    public static <T> Result<T> success(String message,T data) {return new Result(message,data);}

    /**
     * 使用 CodeMsg 枚举创建成功结果
     */
    public static <T> Result<T> success(CodeMsg codeMsg) {
        return new Result(codeMsg);
    }

    /**
     * 使用 CodeMsg 枚举和数据创建成功结果
     */
    public static <T> Result<T> success(CodeMsg codeMsg, T data) {
        return new Result(codeMsg, data);
    }

    public static <T> Result<T> error(String message) {return new Result(message);}
    public static <T> Result<T> error(Integer code, String message) {
        return new Result(code, message);
    }

    /**
     * 使用 CodeMsg 枚举创建错误结果
     */
    public static <T> Result<T> error(CodeMsg codeMsg) {
        return new Result(codeMsg);
    }

    /**
     * 使用 CodeMsg 枚举和数据创建错误结果
     */
    public static <T> Result<T> error(CodeMsg codeMsg, T data) {
        return new Result(codeMsg, data);
    }

}
