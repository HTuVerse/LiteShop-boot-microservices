package org.hongtu.tenant;

/** 租户和门店上下文契约。内部请求头只能在校验服务间密钥后使用。 */
public final class TenantConstants {
    public static final String SESSION_TENANT_ID = "tenantId";
    public static final String SESSION_SUPER_ADMIN = "superAdmin";
    public static final String SESSION_STORE_RESTRICTED = "storeRestricted";
    /** 当前店长已选择的唯一门店。授权门店列表与当前工作门店必须分开保存。 */
    public static final String SESSION_ACTIVE_STORE_ID = "activeStoreId";
    /** 当前账号在所属租户内被授权的全部门店，仅用于校验切换目标。 */
    public static final String SESSION_AUTHORIZED_STORE_IDS = "authorizedStoreIds";
    /** 首次身份验证通过的 tenantId -> tenant userId 映射，仅保存在服务端会话。 */
    public static final String SESSION_WORKSPACE_USERS = "workspaceUsers";
    public static final String SESSION_STORE_IDS = "storeIds";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_SUPER_ADMIN = "X-Super-Admin";
    public static final String HEADER_STORE_RESTRICTED = "X-Store-Restricted";
    public static final String HEADER_STORE_IDS = "X-Store-Ids";
    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";
    public static final String HEADER_PUBLIC_TENANT_ID = "X-Public-Tenant-Id";
    public static final String MQ_TENANT_HEADER = "x-tenant-id";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STORE_ADMIN = "STORE_ADMIN";
    public static final String ROLE_STORE_STAFF = "STORE_STAFF";

    private TenantConstants() {
    }
}
