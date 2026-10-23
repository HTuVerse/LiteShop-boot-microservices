package org.hongtu.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 在集中落库前再次清除差异中的敏感字段。 */
public final class AuditSanitizer {
    private AuditSanitizer() { }

    public static String safeDiff(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode node = mapper.readTree(raw);
            sanitize(node);
            String safe = mapper.writeValueAsString(node);
            return safe.length() > 16000 ? null : safe;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void sanitize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> remove = new ArrayList<>();
            object.fields().forEachRemaining(field -> {
                String name = field.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (name.matches(".*(password|passwd|secret|token|captcha|verifycode|privatekey|apikey|accesskey|ukey|credential|authorization).*"))
                    remove.add(field.getKey());
                else sanitize(field.getValue());
            });
            remove.forEach(object::remove);
        } else if (node.isArray()) {
            node.forEach(AuditSanitizer::sanitize);
        }
    }
}
