package org.hongtu.enums;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

/**
 * 错误码枚举类
 *
 * @author Honghong
 * @since 2025-07-31
 */
@AllArgsConstructor
@Getter
public enum CodeMsg {

    // 通用错误码
    SUCCESS(200, "请求成功"),
    SERVER_ERROR(500, "服务器内部错误"),
    PARAM_ERROR(400, "参数错误"),
    NOT_LOGIN(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FIND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    UPLOAD_SIZE_EXCEEDED(413, "上传文件大小超过限制"),

    // 数据库相关错误码
    DB_ERROR(5001, "数据库操作异常"),
    DB_DUPLICATE_KEY(5002, "数据已存在"),

    // 用户模块错误码 (1000-1999)
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "密码错误"),
    USER_ACCOUNT_LOCKED(1003, "账号已被锁定"),
    USER_ACCOUNT_DISABLED(1004, "账号已被禁用"),
    USER_EXIST(1005, "用户已存在"),

    // 商品模块错误码 (2000-2999)
    PRODUCT_NOT_EXIST(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架"),
    PRODUCT_STOCK_NOT_ENOUGH(2003, "商品库存不足"),

    // 订单模块错误码 (3000-3999)
    ORDER_NOT_EXIST(3001, "订单不存在"),
    ORDER_STATUS_ERROR(3002, "订单状态错误"),
    ORDER_CREATE_FAIL(3003, "订单创建失败"),
    ORDER_PAY_FAIL(3004, "订单支付失败"),
    ORDER_CANCEL_FAIL(3005, "订单取消失败"),

    // 购物车模块错误码 (4000-4999)
    CART_ADD_FAIL(4001, "添加购物车失败"),
    CART_UPDATE_FAIL(4002, "更新购物车失败"),
    CART_DELETE_FAIL(4003, "删除购物车失败"),
    CART_EMPTY(4004, "购物车为空"),

    // 支付模块错误码 (5000-5999)
    PAY_FAIL(5001, "支付失败"),
    PAY_CANCEL(5002, "支付取消"),
    PAY_TIMEOUT(5003, "支付超时"),

    // 文件上传模块错误码 (6000-6999)
    FILE_UPLOAD_FAIL(6001, "文件上传失败"),
    FILE_TYPE_NOT_SUPPORT(6002, "文件类型不支持"),
    FILE_SIZE_EXCEED(6003, "文件大小超过限制"),

    // 短信/邮件模块错误码 (7000-7999)
    SMS_SEND_FAIL(7001, "短信发送失败"),
    SMS_CODE_ERROR(7002, "验证码错误"),
    SMS_CODE_EXPIRED(7003, "验证码已过期"),
    EMAIL_SEND_FAIL(7004, "邮件发送失败"),

    //点餐会话模块错误码 (8000-8999)
    DINNER_NOT_EXIST(8001, "点餐会话不存在"),
    DINNER_STATUS_ERROR(8002, "点餐会话已结束,请重新扫码加入");


    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误信息
     */
    private String msg;


    CodeMsg(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

}
