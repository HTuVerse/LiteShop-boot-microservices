package org.hongtu;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.DishMapper;
import org.hongtu.restaurant.DAO.StoreDishMapper;
import org.hongtu.restaurant.DAO.StoreMenuPolicyMapper;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DTO.StoreDish;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.StoreMenuPolicy;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.VO.StoreDishConfigRequest;
import org.hongtu.restaurant.controller.DishCategoryController;
import org.hongtu.restaurant.service.DishCategoryService;
import org.hongtu.restaurant.service.DishSpecOptionService;
import org.hongtu.restaurant.service.PricingService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.StoreInfoService;
import org.hongtu.restaurant.service.StoreMenuPolicyService;
import org.hongtu.restaurant.service.impl.DishServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 连锁菜单第一阶段的价格继承、策略限制和权限边界测试。 */
class ChainMenuPhaseOneTest {

    @BeforeAll
    static void metadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "chain-menu-tests");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Dish.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, StoreDish.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, StoreMenuPolicy.class);
    }

    @Test
    void effectiveDishUsesStorePriceAndKeepsHeadquarterDefault() {
        Fixture fixture = fixture(false, new BigDecimal("2.0000"));

        DishVO result = fixture.service.getEffectiveDish(100L, 10L, true);

        assertEquals(new BigDecimal("120.00"), result.getEffectiveSalePrice());
        assertEquals(new BigDecimal("100.00"), result.getSalePrice());
        assertEquals(10L, result.getStoreId());
        assertEquals(1, result.getStoreListingStatus());
        assertEquals(true, result.getSalePriceOverridden());
    }

    @Test
    void headquarterDisableOverridesStoreListingStatus() {
        Fixture fixture = fixture(false, new BigDecimal("2.0000"));
        fixture.dish.setMasterStatus(2);

        DishVO result = fixture.service.getEffectiveDish(100L, 10L, false);

        assertEquals(0, result.getListingStatus());
        assertEquals(1, result.getStoreListingStatus());
        assertThrows(BusinessException.class,
                () -> fixture.service.getEffectiveDish(100L, 10L, true));
    }

    @Test
    void storePriceOutsidePolicyRangeIsRejected() {
        Fixture fixture = fixture(true, new BigDecimal("1.1000"));
        StoreDishConfigRequest request = request(new BigDecimal("130.00"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.updateStoreDishConfig(request));

        assertEquals(true, error.getMessage().contains("超出总部允许范围"));
    }

    @Test
    void unchangedExistingOverrideDoesNotBlockOtherStoreChanges() {
        Fixture fixture = fixture(true, new BigDecimal("2.0000"));
        fixture.policy.setAllowPriceOverride(0);
        StoreDishConfigRequest request = request(new BigDecimal("120.0"));
        request.setStock(15);

        assertDoesNotThrow(() -> fixture.service.validateStoreDishConfig(request));
    }

    @Test
    void existingOffShelfStatusDoesNotBlockUnrelatedStoreChanges() {
        Fixture fixture = fixture(true, new BigDecimal("2.0000"));
        fixture.storeDish.setListingStatus(0);
        fixture.policy.setAllowStoreOffShelf(0);
        StoreDishConfigRequest request = request(new BigDecimal("120.00"));
        request.setListingStatus(0).setSoldOut(1);

        assertDoesNotThrow(() -> fixture.service.validateStoreDishConfig(request));
    }

    @Test
    void headquartersCanPublishOutsideStoreSelfServicePriceRange() {
        Fixture fixture = fixture(false, new BigDecimal("1.1000"));

        assertDoesNotThrow(() -> fixture.service.validateStoreDishConfig(
                request(new BigDecimal("130.00"))));
    }

    @Test
    void restrictedStoreCannotOverrideDishCategoryWhenPolicyDisallowsIt() {
        Fixture fixture = fixture(true, new BigDecimal("2.0000"));
        fixture.policy.setAllowCategoryOverride(0);
        StoreDishConfigRequest request = request(new BigDecimal("120.00"));
        request.setCategoryOverrideId(300L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.validateStoreDishConfig(request));

        assertEquals(true, error.getMessage().contains("调整菜品分类"));
    }

    @Test
    void restrictedStoreCannotCreateCategoryWhenPolicyDisallowsIt() {
        DishCategoryService categoryService = mock(DishCategoryService.class);
        StoreDataScopeService scope = mock(StoreDataScopeService.class);
        StoreInfoService storeInfoService = mock(StoreInfoService.class);
        StoreMenuPolicyService policyService = mock(StoreMenuPolicyService.class);
        when(scope.requiresStoreRestriction()).thenReturn(true);
        when(storeInfoService.getById(10L)).thenReturn(new StoreInfo().setId(10L));
        when(policyService.getEffectivePolicy(10L)).thenReturn(new StoreMenuPolicy()
                .setStoreId(10L).setAllowLocalCategory(0));
        DishCategoryController controller = new DishCategoryController();
        ReflectionTestUtils.setField(controller, "dishCategoryService", categoryService);
        ReflectionTestUtils.setField(controller, "storeDataScopeService", scope);
        ReflectionTestUtils.setField(controller, "storeInfoService", storeInfoService);
        ReflectionTestUtils.setField(controller, "storeMenuPolicyService", policyService);
        org.hongtu.restaurant.DTO.DishCategory request = new org.hongtu.restaurant.DTO.DishCategory()
                .setStoreId(10L).setSourceType(1).setCategoryName("门店分类");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.addCategory(request));

        assertEquals(400, error.getCode());
        assertEquals(true, error.getMessage().contains("新增分类"));
    }

    @Test
    void restrictedStoreCannotChangeItsOwnPolicy() {
        StoreMenuPolicyMapper mapper = mock(StoreMenuPolicyMapper.class);
        StoreDataScopeService scope = mock(StoreDataScopeService.class);
        when(scope.requiresStoreRestriction()).thenReturn(true);
        StoreMenuPolicyService service = new StoreMenuPolicyService();
        ReflectionTestUtils.setField(service, "policyMapper", mapper);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scope);

        assertThrows(SecurityException.class,
                () -> service.savePolicy(new StoreMenuPolicy().setStoreId(10L)));
        verify(scope).requiresStoreRestriction();
    }

    @Test
    void headquartersCanUpdateStorePolicy() {
        StoreMenuPolicyMapper mapper = mock(StoreMenuPolicyMapper.class);
        StoreDataScopeService scope = mock(StoreDataScopeService.class);
        StoreInfoService storeInfoService = mock(StoreInfoService.class);
        StoreMenuPolicy existing = new StoreMenuPolicy().setId(99L).setStoreId(10L);
        when(scope.requiresStoreRestriction()).thenReturn(false);
        when(storeInfoService.getById(10L)).thenReturn(new StoreInfo().setId(10L));
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        StoreMenuPolicyService service = new StoreMenuPolicyService();
        ReflectionTestUtils.setField(service, "policyMapper", mapper);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scope);
        ReflectionTestUtils.setField(service, "storeInfoService", storeInfoService);
        StoreMenuPolicy request = new StoreMenuPolicy()
                .setStoreId(10L)
                .setAllowLocalDish(0)
                .setAllowLocalCategory(0)
                .setAllowCategoryOverride(0)
                .setAllowPriceOverride(1)
                .setAllowStoreOffShelf(0)
                .setAllowStoreExit(1)
                .setMinPriceRate(new BigDecimal("0.8000"))
                .setMaxPriceRate(new BigDecimal("1.2000"))
                .setRequireApproval(1);

        StoreMenuPolicy result = service.savePolicy(request);

        assertEquals(99L, result.getId());
        assertEquals(0, result.getAllowLocalDish());
        assertEquals(new BigDecimal("1.2000"), result.getMaxPriceRate());
        verify(mapper).updateById(request);
    }

    private static Fixture fixture(boolean restricted, BigDecimal maxRate) {
        DishMapper dishMapper = mock(DishMapper.class);
        StoreDishMapper storeDishMapper = mock(StoreDishMapper.class);
        StoreDataScopeService scope = mock(StoreDataScopeService.class);
        StoreMenuPolicyService policyService = mock(StoreMenuPolicyService.class);
        DishSpecOptionService specService = mock(DishSpecOptionService.class);
        DishCategoryService categoryService = mock(DishCategoryService.class);
        StoreInfoService storeInfoService = mock(StoreInfoService.class);
        Dish dish = new Dish()
                .setId(100L)
                .setDishName("总部招牌菜")
                .setSalePrice(new BigDecimal("100.00"))
                .setSourceType(0)
                .setMasterStatus(1)
                .setVersion(1)
                .setDeleted(0);
        StoreDish storeDish = new StoreDish()
                .setId(200L)
                .setStoreId(10L)
                .setDishId(100L)
                .setSalePriceOverride(new BigDecimal("120.00"))
                .setListingStatus(1)
                .setSoldOut(0)
                .setInventoryMode(1)
                .setStock(20)
                .setSalesCount(0)
                .setDeleted(0);
        StoreMenuPolicy policy = new StoreMenuPolicy()
                .setStoreId(10L)
                .setAllowLocalDish(1)
                .setAllowLocalCategory(1)
                .setAllowCategoryOverride(1)
                .setAllowPriceOverride(1)
                .setAllowStoreOffShelf(1)
                .setAllowStoreExit(1)
                .setMinPriceRate(BigDecimal.ZERO)
                .setMaxPriceRate(maxRate)
                .setRequireApproval(0);

        when(scope.requiresStoreRestriction()).thenReturn(restricted);
        when(scope.getAllowedStoreIds()).thenReturn(List.of(10L));
        when(dishMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dish));
        when(dishMapper.selectById(100L)).thenReturn(dish);
        when(storeDishMapper.selectList(any(Wrapper.class))).thenReturn(List.of(storeDish));
        when(storeDishMapper.selectOne(any(Wrapper.class))).thenReturn(storeDish);
        when(policyService.getEffectivePolicy(10L)).thenReturn(policy);
        when(specService.selectSpecOptionsByDishId(100L)).thenReturn(List.of());
        when(storeInfoService.getById(10L)).thenReturn(new StoreInfo().setId(10L));

        DishServiceImpl service = new DishServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", dishMapper);
        ReflectionTestUtils.setField(service, "storeDishMapper", storeDishMapper);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scope);
        ReflectionTestUtils.setField(service, "storeMenuPolicyService", policyService);
        ReflectionTestUtils.setField(service, "dishSpecOptionService", specService);
        ReflectionTestUtils.setField(service, "dishCategoryService", categoryService);
        ReflectionTestUtils.setField(service, "storeInfoService", storeInfoService);
        ReflectionTestUtils.setField(service, "pricingService", mock(PricingService.class));
        return new Fixture(service, dish, storeDish, policy);
    }

    private static StoreDishConfigRequest request(BigDecimal price) {
        StoreDishConfigRequest request = new StoreDishConfigRequest();
        request.setStoreId(10L);
        request.setDishId(100L);
        request.setSalePriceOverride(price);
        request.setListingStatus(1);
        request.setSoldOut(0);
        request.setInventoryMode(1);
        request.setStock(20);
        return request;
    }

    private record Fixture(DishServiceImpl service, Dish dish, StoreDish storeDish,
                           StoreMenuPolicy policy) {
    }
}
