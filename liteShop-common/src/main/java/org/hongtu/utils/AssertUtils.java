package org.hongtu.utils;

import org.hongtu.enums.CodeMsg;
import org.hongtu.exception.BusinessException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

/**
 * 断言工具类
 * 用于简化业务逻辑中的条件判断和异常抛出
 * 
 * @author Honghong
 * @since 2025-07-31
 */
public class AssertUtils {

    /**
     * 断言对象不为空
     *
     * @param object 对象
     * @param message 错误消息
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言对象不为空
     *
     * @param object 对象
     * @param codeMsg 错误码枚举
     */
    public static void notNull(Object object, CodeMsg codeMsg) {
        if (object == null) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }

    /**
     * 断言字符串不为空
     *
     * @param text 字符串
     * @param message 错误消息
     */
    public static void hasText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言字符串不为空
     *
     * @param text 字符串
     * @param codeMsg 错误码枚举
     */
    public static void hasText(String text, CodeMsg codeMsg) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }

    /**
     * 断言集合不为空
     *
     * @param collection 集合
     * @param message 错误消息
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言集合不为空
     *
     * @param collection 集合
     * @param codeMsg 错误码枚举
     */
    public static void notEmpty(Collection<?> collection, CodeMsg codeMsg) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }

    /**
     * 断言Map不为空
     *
     * @param map Map
     * @param message 错误消息
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (CollectionUtils.isEmpty(map)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言Map不为空
     *
     * @param map Map
     * @param codeMsg 错误码枚举
     */
    public static void notEmpty(Map<?, ?> map, CodeMsg codeMsg) {
        if (CollectionUtils.isEmpty(map)) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }

    /**
     * 断言数组不为空
     *
     * @param array 数组
     * @param message 错误消息
     */
    public static void notEmpty(Object[] array, String message) {
        if (ObjectUtils.isEmpty(array)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言数组不为空
     *
     * @param array 数组
     * @param codeMsg 错误码枚举
     */
    public static void notEmpty(Object[] array, CodeMsg codeMsg) {
        if (ObjectUtils.isEmpty(array)) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }

    /**
     * 断言表达式为真
     *
     * @param expression 表达式
     * @param message 错误消息
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言表达式为真
     *
     * @param expression 表达式
     * @param codeMsg 错误码枚举
     */
    public static void isTrue(boolean expression, CodeMsg codeMsg) {
        if (!expression) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }

    /**
     * 断言表达式为假
     *
     * @param expression 表达式
     * @param message 错误消息
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言表达式为假
     *
     * @param expression 表达式
     * @param codeMsg 错误码枚举
     */
    public static void isFalse(boolean expression, CodeMsg codeMsg) {
        if (expression) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }

    /**
     * 断言对象为指定类型
     *
     * @param type 类型
     * @param obj 对象
     * @param message 错误消息
     */
    public static void isInstanceOf(Class<?> type, Object obj, String message) {
        if (!type.isInstance(obj)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言对象为指定类型
     *
     * @param type 类型
     * @param obj 对象
     * @param codeMsg 错误码枚举
     */
    public static void isInstanceOf(Class<?> type, Object obj, CodeMsg codeMsg) {
        if (!type.isInstance(obj)) {
            throw new BusinessException(codeMsg.getCode(), codeMsg.getMsg());
        }
    }
} 