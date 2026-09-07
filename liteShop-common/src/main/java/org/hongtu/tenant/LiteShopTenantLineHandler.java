package org.hongtu.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Locale;
import java.util.Set;

/**
 * 多租户 SQL 改写处理器：为 SQL 自动追加 tenant_id 条件（INSERT 自动填充 tenant_id 列）。
 * <p>
 * 通过 liteshop.tenant.enabled=true 启用（见 MybatisPlusConfig）。
 */
public class LiteShopTenantLineHandler implements TenantLineHandler {

    /**
     * 无 tenant_id 列的全局表（与建表脚本保持一致）
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "sys_tenant",
            "sys_permission",
            "sys_website_config"
    );

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.getTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }
}
