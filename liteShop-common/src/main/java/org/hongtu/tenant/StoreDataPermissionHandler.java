package org.hongtu.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 门店数据权限 SQL 处理器。
 * <p>
 * 仅在 {@link StoreContext#isRestricted()} 为 true 时生效；商户管理员和平台超级管理员
 * 不受门店范围限制。表自身是门店时用主键 id，其余门店业务表用 store_id。
 */
public class StoreDataPermissionHandler implements MultiDataPermissionHandler {

    private static final Map<String, String> STORE_COLUMNS = Map.of(
            "store_info", "id",
            "dish_category", "store_id",
            "dish", "store_id",
            "table_info", "store_id",
            "table_session", "store_id",
            "restaurant_order", "store_id",
            "printer_info", "store_id",
            "print_template", "store_id"
    );

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!StoreContext.isRestricted()) {
            return null;
        }
        String tableName = table.getUnquotedName().toLowerCase(Locale.ROOT);
        String storeColumn = STORE_COLUMNS.get(tableName);
        if (storeColumn == null) {
            return null;
        }

        List<Long> storeIds = StoreContext.getStoreIds();
        if (storeIds.isEmpty()) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }

        Table qualifier = table.getAlias() == null
                ? new Table(table.getName())
                : new Table(table.getAlias().getName());
        Column column = new Column(qualifier, storeColumn);
        LongValue[] values = storeIds.stream().map(LongValue::new).toArray(LongValue[]::new);
        return new InExpression(column, new ParenthesedExpressionList<>(values));
    }
}
