package org.hongtu.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 仅按业务明确列出的安全字段生成修改前后差异。 */
@Component
@RequiredArgsConstructor
public class AuditChanges {
    private final ObjectMapper mapper;

    public void capture(Object before, Object after, String... safeFields) {
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        for (String field : safeFields) {
            if (!field.matches("[a-zA-Z][a-zA-Z0-9_]*"))
                throw new IllegalArgumentException("审计字段名不合法");
            Object oldValue = read(before, field);
            Object newValue = read(after, field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("before", oldValue);
                values.put("after", newValue);
                changes.put(field, values);
            }
        }
        if (changes.isEmpty()) return;
        try {
            AuditContext.diff(mapper.writeValueAsString(changes));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("审计差异序列化失败", error);
        }
    }

    private Object read(Object value, String field) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) return map.get(field);
        String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        try {
            Method method;
            try {
                method = value.getClass().getMethod(getter);
            } catch (NoSuchMethodException ignored) {
                method = value.getClass().getMethod("is" + getter.substring(3));
            }
            Object result = method.invoke(value);
            if (result instanceof Enum<?> choice) return choice.name();
            if (result instanceof Number || result instanceof Boolean || result instanceof CharSequence)
                return result;
            return result == null ? null : String.valueOf(result);
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException("审计字段不存在: " + field, error);
        }
    }
}
