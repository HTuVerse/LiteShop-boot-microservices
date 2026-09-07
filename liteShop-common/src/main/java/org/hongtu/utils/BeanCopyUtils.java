package org.hongtu.utils;

/**
 * 功能：DTO转VO
 * 作者：
 * 时间：2024/11/19 上午1:05
 */
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.stream.Collectors;
/**
 * @author xct
 * @version 1.0
 * @date 2023-06-08 15:01
 */
public class BeanCopyUtils {
    private BeanCopyUtils() {
    }

    public static <V> V copyBean(Object source, Class<V> clazz) {
        try {
            V result = clazz.getDeclaredConstructor().newInstance();
            // 实现属性拷贝
            BeanUtils.copyProperties(source, result);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("无法创建目标类型: " + clazz.getName(), e);
        }
    }

    public static <O, V> List<V> copyBeanList(List<O> list, Class<V> clazz) {
        return list.stream()
                .map(o -> copyBean(o, clazz))
                .collect(Collectors.toList());
    }
}


