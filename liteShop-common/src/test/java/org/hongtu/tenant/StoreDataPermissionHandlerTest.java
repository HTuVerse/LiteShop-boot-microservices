package org.hongtu.tenant;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreDataPermissionHandlerTest {

    private final StoreDataPermissionHandler handler = new StoreDataPermissionHandler();

    @AfterEach
    void tearDown() {
        StoreContext.clear();
    }

    @Test
    void shouldNotFilterTenantOrPlatformAdmin() {
        assertNull(handler.getSqlSegment(new Table("dish"), null, "test.select"));
    }

    @Test
    void shouldFilterStoreOwnedTableWithAlias() {
        StoreContext.setRestricted(true, List.of(11L, 22L));
        Table table = new Table("dish");
        table.setAlias(new Alias("d"));

        Expression expression = handler.getSqlSegment(table, null, "test.select");

        assertEquals("d.store_id IN (11, 22)", expression.toString());
    }

    @Test
    void shouldUseIdForStoreInfo() {
        StoreContext.setRestricted(true, List.of(11L));
        Expression expression = handler.getSqlSegment(new Table("store_info"), null, "test.select");
        assertEquals("store_info.id IN (11)", expression.toString());
    }

    @Test
    void shouldDenyAllWhenStoreRoleHasNoAssignments() {
        StoreContext.setRestricted(true, List.of());
        Expression expression = handler.getSqlSegment(new Table("restaurant_order"), null, "test.select");
        assertEquals("1 = 0", expression.toString());
    }

    @Test
    void shouldIgnoreTableWithoutStoreColumn() {
        StoreContext.setRestricted(true, List.of(11L));
        assertNull(handler.getSqlSegment(new Table("restaurant_order_item"), null, "test.select"));
    }
}
