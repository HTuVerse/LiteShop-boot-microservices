package org.hongtu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.UserBasicDTO;
import org.hongtu.restaurant.DAO.DishMapper;
import org.hongtu.restaurant.DAO.MenuChangeRecordMapper;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DTO.MenuChangeRecord;
import org.hongtu.restaurant.VO.MenuChangeRecordVO;
import org.hongtu.restaurant.enums.MenuChangeStatusEnum;
import org.hongtu.restaurant.service.MenuChangeService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 菜单变更记录的菜品、申请人及驳回原因填充测试。
 */
class MenuChangeRecordEnrichmentTest {
    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "menu-change-record"),
                MenuChangeRecord.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageAddsDishApplicantAndRejectReason() {
        MenuChangeRecordMapper recordMapper = mock(MenuChangeRecordMapper.class);
        DishMapper dishMapper = mock(DishMapper.class);
        UserFeignClient userClient = mock(UserFeignClient.class);
        StoreDataScopeService scopeService = mock(StoreDataScopeService.class);
        MenuChangeRecord record = new MenuChangeRecord().setId(1L).setDishId(10L).setStoreId(20L)
                .setRequestedBy(30L).setStatus(MenuChangeStatusEnum.REJECTED.getCode())
                .setReviewRemark("售价低于门店策略下限");
        Page<MenuChangeRecord> source = new Page<>(1, 10, 1);
        source.setRecords(List.of(record));
        when(recordMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(source);
        when(dishMapper.selectBatchIds(any(Collection.class)))
                .thenReturn(List.of(new Dish().setId(10L).setDishName("招牌牛肉饭")));
        when(userClient.getUserBasics(any()))
                .thenReturn(Result.success(List.of(new UserBasicDTO().setId(30L)
                        .setUsername("store_admin").setNickname("机场店店长"))));

        MenuChangeService service = new MenuChangeService();
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "dishMapper", dishMapper);
        ReflectionTestUtils.setField(service, "userFeignClient", userClient);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scopeService);

        MenuChangeRecordVO result = service.page(1, 10, null, null, null, null).getRecords().getFirst();

        assertEquals("招牌牛肉饭", result.getDishName());
        assertEquals("机场店店长", result.getRequestedByName());
        assertEquals("售价低于门店策略下限", result.getRejectReason());
    }
}
