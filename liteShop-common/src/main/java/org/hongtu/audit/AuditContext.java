package org.hongtu.audit;

/** 当前请求的业务对象信息，由业务层在完成脱敏后补充。 */
public final class AuditContext {
    private static final ThreadLocal<Details> CURRENT = ThreadLocal.withInitial(Details::new);

    private AuditContext() { }

    public static void target(String objectId, Long subjectTenantId) {
        Details details = CURRENT.get();
        details.objectId = objectId;
        details.subjectTenantId = subjectTenantId;
    }

    public static void summary(String summary) {
        CURRENT.get().summary = summary;
    }

    /** 只允许传入已经按字段白名单筛选的 JSON 差异，不能传原始请求体。 */
    public static void diff(String safeDiffJson) {
        CURRENT.get().diffJson = safeDiffJson;
    }

    static Details details() { return CURRENT.get(); }
    static void clear() { CURRENT.remove(); }

    static final class Details {
        String objectId;
        Long subjectTenantId;
        String summary;
        String diffJson;
    }
}
