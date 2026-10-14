package org.hongtu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.MemberPricingDTO;
import org.hongtu.restaurant.DAO.*;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.VO.PricingQuoteRequest;
import org.hongtu.restaurant.VO.PricingQuoteVO;
import org.hongtu.restaurant.service.PricingService;
import org.hongtu.restaurant.enums.PricingEnums.*;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 统一定价计算规则测试。
 */
class PricingServiceTest {
    private PricingCampaignMapper campaignMapper;
    private PricingRuleMapper ruleMapper;
    private PricingCampaignScopeMapper scopeMapper;
    private PricingCampaignAudienceMapper audienceMapper;
    private MemberDishPriceMapper memberPriceMapper;
    private DishMapper dishMapper;
    private StoreDishMapper storeDishMapper;
    private StoreGroupMapper storeGroupMapper;
    private StoreGroupMemberMapper storeGroupMemberMapper;
    private UserFeignClient userFeignClient;
    private PricingService service;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entityType : List.of(PricingCampaign.class, PricingRule.class,
                PricingCampaignScope.class, PricingCampaignAudience.class,
                MemberDishPrice.class, StoreDish.class, StoreGroup.class, StoreGroupMember.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, entityType.getName()), entityType);
        }
        campaignMapper = mock(PricingCampaignMapper.class);
        ruleMapper = mock(PricingRuleMapper.class);
        scopeMapper = mock(PricingCampaignScopeMapper.class);
        audienceMapper = mock(PricingCampaignAudienceMapper.class);
        memberPriceMapper = mock(MemberDishPriceMapper.class);
        dishMapper = mock(DishMapper.class);
        storeDishMapper = mock(StoreDishMapper.class);
        storeGroupMapper = mock(StoreGroupMapper.class);
        storeGroupMemberMapper = mock(StoreGroupMemberMapper.class);
        userFeignClient = mock(UserFeignClient.class);
        service = new PricingService(campaignMapper, ruleMapper, scopeMapper, audienceMapper,
                memberPriceMapper, dishMapper, storeDishMapper, storeGroupMapper, storeGroupMemberMapper,
                userFeignClient,
                mock(StoreDataScopeService.class), new ObjectMapper());

        when(dishMapper.selectById(10L)).thenReturn(new Dish().setId(10L)
                .setDishName("测试菜品").setCategoryId(20L).setSalePrice(new BigDecimal("10.00"))
                .setDeleted(0));
        when(storeDishMapper.selectOne(any())).thenReturn(null);
        when(memberPriceMapper.selectList(any())).thenReturn(List.of());
        when(storeGroupMapper.selectList(any())).thenReturn(List.of());
        when(storeGroupMemberMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void secondItemEightyPercentIsAllocatedToLine() {
        PricingCampaign campaign = new PricingCampaign().setId(1L).setCampaignName("第二件八折")
                .setCampaignType(CampaignType.QUANTITY_DISCOUNT).setExclusiveGroup("QUANTITY")
                .setStackMode(StackMode.STACKABLE).setStatus(1).setDeleted(0).setPriority(100);
        when(campaignMapper.selectList(any())).thenReturn(List.of(campaign));
        when(ruleMapper.selectList(any())).thenReturn(List.of(new PricingRule().setCampaignId(1L)
                .setConditionType(ConditionType.QUANTITY).setMinimumQuantity(2).setNthQuantity(2)
                .setActionType(ActionType.DISCOUNT_RATE).setActionValue(new BigDecimal("0.80"))
                .setRepeatMode(RepeatMode.ONCE)));
        when(scopeMapper.selectList(any())).thenReturn(List.of(new PricingCampaignScope()
                .setCampaignId(1L).setTargetType(TargetType.DISH).setTargetId(10L)));
        when(audienceMapper.selectList(any())).thenReturn(List.of(new PricingCampaignAudience()
                .setCampaignId(1L).setAudienceType(AudienceType.ALL)));

        PricingQuoteVO quote = service.quote(request(2, null));

        assertEquals(new BigDecimal("20.00"), quote.getListAmount());
        assertEquals(new BigDecimal("18.00"), quote.getPayableAmount());
        assertEquals(new BigDecimal("2.00"), quote.getCampaignDiscountAmount());
        assertEquals("第二件八折", quote.getLines().getFirst().getAdjustments().getFirst().getCampaignName());
    }

    @Test
    void memberDishPriceBeatsNormalSalePrice() {
        when(campaignMapper.selectList(any())).thenReturn(List.of());
        when(memberPriceMapper.selectList(any())).thenReturn(List.of(new MemberDishPrice().setId(9L)
                .setDishId(10L).setMemberLevelCode(2).setMemberPrice(new BigDecimal("8.50"))
                .setStatus(1).setDeleted(0)));

        PricingQuoteVO quote = service.quote(request(1, 2));

        assertEquals(new BigDecimal("8.50"), quote.getPayableAmount());
        assertEquals("MEMBER_PRICE", quote.getLines().getFirst().getPriceSource());
        assertEquals(new BigDecimal("1.50"), quote.getCampaignDiscountAmount());
    }

    @Test
    void savingMemberPriceChecksOnlySelectedDish() {
        MemberDishPrice price = new MemberDishPrice().setDishId(10L).setMemberLevelCode(2)
                .setMemberPrice(new BigDecimal("8.50")).setPriority(0).setStatus(1);

        service.saveMemberPrice(price);

        verify(dishMapper).selectById(10L);
        verify(dishMapper, never()).selectList(any());
        verify(memberPriceMapper).insert(price);
    }

    @Test
    void memberLevelIsLoadedFromMinimalPricingEndpoint() {
        when(campaignMapper.selectList(any())).thenReturn(List.of());
        when(memberPriceMapper.selectList(any())).thenReturn(List.of(new MemberDishPrice().setId(9L)
                .setDishId(10L).setMemberLevelCode(2).setMemberPrice(new BigDecimal("8.50"))
                .setStatus(1).setDeleted(0)));
        when(userFeignClient.getMemberPricing(88L)).thenReturn(Result.success(
                new MemberPricingDTO().setId(88L).setLevel(2)));
        PricingQuoteRequest request = request(1, null);
        request.setUserId(88L);

        PricingQuoteVO quote = service.quote(request);

        assertEquals(new BigDecimal("8.50"), quote.getPayableAmount());
        verify(userFeignClient).getMemberPricing(88L);
        verify(userFeignClient, never()).getUserById(any());
    }

    @Test
    void repeatedQuantityDiscountUsesMinimumQuantityAsGroupSize() {
        PricingCampaign campaign = new PricingCampaign().setId(2L).setCampaignName("满三件第二件八折")
                .setCampaignType(CampaignType.QUANTITY_DISCOUNT).setExclusiveGroup("QUANTITY")
                .setStackMode(StackMode.STACKABLE).setStatus(1).setDeleted(0).setPriority(100);
        when(campaignMapper.selectList(any())).thenReturn(List.of(campaign));
        when(ruleMapper.selectList(any())).thenReturn(List.of(new PricingRule().setCampaignId(2L)
                .setConditionType(ConditionType.QUANTITY).setMinimumQuantity(3).setNthQuantity(2)
                .setActionType(ActionType.DISCOUNT_RATE).setActionValue(new BigDecimal("0.80"))
                .setRepeatMode(RepeatMode.EVERY_GROUP)));
        when(scopeMapper.selectList(any())).thenReturn(List.of(new PricingCampaignScope()
                .setCampaignId(2L).setTargetType(TargetType.DISH).setTargetId(10L)));
        when(audienceMapper.selectList(any())).thenReturn(List.of(new PricingCampaignAudience()
                .setCampaignId(2L).setAudienceType(AudienceType.ALL)));

        PricingQuoteVO quote = service.quote(request(4, null));

        assertEquals(new BigDecimal("38.00"), quote.getPayableAmount());
        assertEquals(new BigDecimal("2.00"), quote.getCampaignDiscountAmount());
    }

    @Test
    void memberDishPriceHonorsConfiguredPriorityInsteadOfLowestPrice() {
        when(campaignMapper.selectList(any())).thenReturn(List.of());
        when(memberPriceMapper.selectList(any())).thenReturn(List.of(
                new MemberDishPrice().setId(10L).setStoreId(30L).setDishId(10L)
                        .setMemberLevelCode(2).setMemberPrice(new BigDecimal("9.00")).setPriority(100),
                new MemberDishPrice().setId(9L).setStoreId(30L).setDishId(10L)
                        .setMemberLevelCode(2).setMemberPrice(new BigDecimal("8.00")).setPriority(1)));

        PricingQuoteVO quote = service.quote(request(1, 2));

        assertEquals(new BigDecimal("9.00"), quote.getPayableAmount());
    }

    @Test
    void storeGroupScopeMatchesOnlyItsMemberStores() {
        PricingCampaign campaign = new PricingCampaign().setId(2L).setCampaignName("分组专享价")
                .setCampaignType(CampaignType.PROMOTION).setExclusiveGroup("DIRECT_PRICE")
                .setStackMode(StackMode.EXCLUSIVE).setStatus(1).setDeleted(0).setPriority(100);
        when(campaignMapper.selectList(any())).thenReturn(List.of(campaign));
        when(ruleMapper.selectList(any())).thenReturn(List.of(new PricingRule().setCampaignId(2L)
                .setConditionType(ConditionType.ALWAYS).setActionType(ActionType.FIXED_PRICE)
                .setActionValue(new BigDecimal("8.00"))));
        when(scopeMapper.selectList(any())).thenReturn(List.of(new PricingCampaignScope()
                .setCampaignId(2L).setStoreGroupId(8L).setTargetType(TargetType.ALL)));
        when(audienceMapper.selectList(any())).thenReturn(List.of(new PricingCampaignAudience()
                .setCampaignId(2L).setAudienceType(AudienceType.ALL)));
        when(storeGroupMapper.selectList(any())).thenReturn(List.of(new StoreGroup()
                .setId(8L).setStatus(1).setDeleted(0)));
        when(storeGroupMemberMapper.selectList(any())).thenReturn(List.of(new StoreGroupMember()
                .setGroupId(8L).setStoreId(30L)));

        PricingQuoteVO quote = service.quote(request(1, null));

        assertEquals(new BigDecimal("8.00"), quote.getPayableAmount());
        assertEquals("分组专享价", quote.getLines().getFirst().getAvailableCampaigns()
                .getFirst().getCampaignName());
    }

    private PricingQuoteRequest request(int quantity, Integer memberLevel) {
        PricingQuoteRequest request = new PricingQuoteRequest();
        request.setStoreId(30L);
        request.setMemberLevelCode(memberLevel);
        PricingQuoteRequest.Item item = new PricingQuoteRequest.Item();
        item.setDishId(10L);
        item.setQuantity(quantity);
        item.setSpecExtraPrice(BigDecimal.ZERO);
        request.setItems(List.of(item));
        return request;
    }
}
