package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.MemberPricingDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.*;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.VO.*;
import org.hongtu.restaurant.enums.PricingEnums.*;
import org.hongtu.api.util.FeignResultUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 唯一的菜品定价入口。菜品、购物车和订单不得自行拼接活动价。
 */
@Service
@RequiredArgsConstructor
public class PricingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PricingCampaignMapper campaignMapper;
    private final PricingRuleMapper ruleMapper;
    private final PricingCampaignScopeMapper scopeMapper;
    private final PricingCampaignAudienceMapper audienceMapper;
    private final MemberDishPriceMapper memberPriceMapper;
    private final DishMapper dishMapper;
    private final StoreDishMapper storeDishMapper;
    private final StoreGroupMapper storeGroupMapper;
    private final StoreGroupMemberMapper storeGroupMemberMapper;
    private final UserFeignClient userFeignClient;
    private final StoreDataScopeService storeDataScopeService;
    private final ObjectMapper objectMapper;

    /** 分页查询当前账号可见的定价活动。 */
    public Page<PricingCampaign> pageCampaigns(int current, int size, String name,
                                                CampaignType campaignType, Integer status) {
        List<Long> visibleIds = visibleCampaignIds();
        if (storeDataScopeService.requiresStoreRestriction() && visibleIds.isEmpty()) {
            return new Page<>(Math.max(1, current), Math.min(100, Math.max(1, size)));
        }
        return campaignMapper.selectPage(new Page<>(Math.max(1, current), Math.min(100, Math.max(1, size))),
                new LambdaQueryWrapper<PricingCampaign>()
                        .eq(PricingCampaign::getDeleted, 0)
                        .in(storeDataScopeService.requiresStoreRestriction(), PricingCampaign::getId, visibleIds)
                        .like(StringUtils.hasText(name), PricingCampaign::getCampaignName, name)
                        .eq(campaignType != null, PricingCampaign::getCampaignType, campaignType)
                        .eq(status != null, PricingCampaign::getStatus, status)
                        .orderByDesc(PricingCampaign::getPriority)
                        .orderByDesc(PricingCampaign::getId));
    }

    /** 获取定价活动及其规则、范围和适用人群。 */
    public PricingCampaignDetailVO getCampaign(Long id) {
        PricingCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null || Integer.valueOf(1).equals(campaign.getDeleted())) {
            throw new BusinessException("定价活动不存在");
        }
        PricingCampaignDetailVO vo = new PricingCampaignDetailVO();
        vo.setCampaign(campaign);
        vo.setRule(ruleMapper.selectOne(new LambdaQueryWrapper<PricingRule>()
                .eq(PricingRule::getCampaignId, id).last("LIMIT 1")));
        vo.setScopes(scopeMapper.selectList(new LambdaQueryWrapper<PricingCampaignScope>()
                .eq(PricingCampaignScope::getCampaignId, id)));
        vo.setAudiences(audienceMapper.selectList(new LambdaQueryWrapper<PricingCampaignAudience>()
                .eq(PricingCampaignAudience::getCampaignId, id)));
        ensureCampaignVisible(vo.getScopes());
        return vo;
    }

    /** 新增或更新一套完整的定价活动配置。 */
    @Transactional(rollbackFor = Exception.class)
    public PricingCampaignDetailVO saveCampaign(PricingCampaignSaveRequest request) {
        validateCampaign(request);
        LocalDateTime now = LocalDateTime.now();
        PricingCampaign entity;
        if (request.getId() == null) {
            ensureCampaignCodeUnique(request.getCampaignCode(), null);
            entity = copyCampaign(request, new PricingCampaign())
                    .setVersion(1).setDeleted(0).setCreatedAt(now).setUpdatedAt(now);
            campaignMapper.insert(entity);
        } else {
            entity = campaignMapper.selectById(request.getId());
            if (entity == null || Integer.valueOf(1).equals(entity.getDeleted())) {
                throw new BusinessException("定价活动不存在");
            }
            ensureCampaignManageable(request.getId());
            Integer currentVersion = defaultInt(entity.getVersion(), 1);
            if (request.getExpectedVersion() != null
                    && !Objects.equals(currentVersion, request.getExpectedVersion())) {
                throw new BusinessException("活动已被其他人修改，请刷新后重试");
            }
            ensureCampaignCodeUnique(request.getCampaignCode(), request.getId());
            copyCampaign(request, entity)
                    .setVersion(currentVersion + 1).setUpdatedAt(now);
            int updated = campaignMapper.update(entity, new LambdaUpdateWrapper<PricingCampaign>()
                    .eq(PricingCampaign::getId, entity.getId())
                    .eq(PricingCampaign::getDeleted, 0)
                    .eq(PricingCampaign::getVersion, currentVersion));
            if (updated == 0) {
                throw new BusinessException("活动已被其他人修改，请刷新后重试");
            }
            ruleMapper.delete(new LambdaQueryWrapper<PricingRule>().eq(PricingRule::getCampaignId, entity.getId()));
            scopeMapper.delete(new LambdaQueryWrapper<PricingCampaignScope>()
                    .eq(PricingCampaignScope::getCampaignId, entity.getId()));
            audienceMapper.delete(new LambdaQueryWrapper<PricingCampaignAudience>()
                    .eq(PricingCampaignAudience::getCampaignId, entity.getId()));
        }
        PricingRule rule = request.getRule();
        rule.setId(null);
        rule.setCampaignId(entity.getId());
        ruleMapper.insert(rule);
        List<PricingCampaignScope> scopes = request.getScopes() == null || request.getScopes().isEmpty()
                ? List.of(new PricingCampaignScope().setTargetType(TargetType.ALL)) : request.getScopes();
        for (PricingCampaignScope scope : scopes) {
            scope.setId(null).setCampaignId(entity.getId());
            scopeMapper.insert(scope);
        }
        List<PricingCampaignAudience> audiences = request.getAudiences() == null
                || request.getAudiences().isEmpty()
                ? List.of(new PricingCampaignAudience().setAudienceType(AudienceType.ALL)) : request.getAudiences();
        for (PricingCampaignAudience audience : audiences) {
            audience.setId(null).setCampaignId(entity.getId());
            audienceMapper.insert(audience);
        }
        return getCampaign(entity.getId());
    }

    /** 逻辑删除定价活动，保留历史订单审计依据。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCampaign(Long id) {
        PricingCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) return;
        ensureCampaignManageable(id);
        campaign.setDeleted(1).setStatus(0).setUpdatedAt(LocalDateTime.now());
        campaignMapper.updateById(campaign);
    }

    /** 只更新活动状态，避免状态切换时重写规则、范围和适用人群。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateCampaignStatus(Long id, Integer status) {
        validateStatus(status);
        PricingCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null || Integer.valueOf(1).equals(campaign.getDeleted())) {
            throw new BusinessException("定价活动不存在");
        }
        ensureCampaignManageable(id);
        Integer currentVersion = defaultInt(campaign.getVersion(), 1);
        int updated = campaignMapper.update(null, new LambdaUpdateWrapper<PricingCampaign>()
                .set(PricingCampaign::getStatus, status)
                .set(PricingCampaign::getUpdatedAt, LocalDateTime.now())
                .set(PricingCampaign::getVersion, currentVersion + 1)
                .eq(PricingCampaign::getId, id)
                .eq(PricingCampaign::getDeleted, 0)
                .eq(PricingCampaign::getVersion, currentVersion));
        if (updated == 0) {
            throw new BusinessException("活动已被其他人修改，请刷新后重试");
        }
    }

    /** 分页查询当前账号可管理的常态会员菜品价。 */
    public Page<MemberDishPrice> pageMemberPrices(int current, int size, Long storeId,
                                                   Long dishId, Integer memberLevelCode) {
        if (storeId != null) storeDataScopeService.checkStoreAccess(storeId);
        if (storeId == null && storeDataScopeService.requiresStoreRestriction()
                && storeDataScopeService.getAllowedStoreIds().isEmpty()) {
            return new Page<>(Math.max(1, current), Math.min(100, Math.max(1, size)));
        }
        LambdaQueryWrapper<MemberDishPrice> query = new LambdaQueryWrapper<MemberDishPrice>()
                        .eq(MemberDishPrice::getDeleted, 0)
                        .eq(storeId != null, MemberDishPrice::getStoreId, storeId)
                        .eq(dishId != null, MemberDishPrice::getDishId, dishId)
                        .eq(memberLevelCode != null, MemberDishPrice::getMemberLevelCode, memberLevelCode)
                        .orderByDesc(MemberDishPrice::getPriority)
                        .orderByDesc(MemberDishPrice::getId);
        if (storeId == null && storeDataScopeService.requiresStoreRestriction()) {
            query.in(MemberDishPrice::getStoreId, storeDataScopeService.getAllowedStoreIds());
        }
        return memberPriceMapper.selectPage(new Page<>(Math.max(1, current), Math.min(100, Math.max(1, size))), query);
    }

    /** 只查询会员价下拉所需的少量菜品，不执行规格、分类和实时定价装配。 */
    public List<MemberPriceDishOptionVO> listMemberPriceDishOptions(Long storeId, String keyword,
                                                                    Long includeDishId, Integer limit) {
        if (storeDataScopeService.requiresStoreRestriction() && storeId == null) {
            throw new BusinessException("门店账号查询菜品时必须指定门店");
        }
        if (storeId != null) storeDataScopeService.checkStoreAccess(storeId);
        int max = Math.min(50, Math.max(1, defaultInt(limit, 30)));
        int candidateLimit = storeId == null ? max : max * 3;
        LambdaQueryWrapper<Dish> query = new LambdaQueryWrapper<Dish>()
                .select(Dish::getId, Dish::getDishName, Dish::getSourceType, Dish::getOwnerStoreId,
                        Dish::getStoreId, Dish::getMasterStatus, Dish::getDeleted, Dish::getSortOrder)
                .eq(Dish::getDeleted, 0)
                .eq(Dish::getMasterStatus, 1)
                .like(StringUtils.hasText(keyword), Dish::getDishName, keyword);
        if (storeId == null) {
            query.and(wrapper -> wrapper.eq(Dish::getSourceType, 0)
                    .or(legacy -> legacy.isNull(Dish::getSourceType).isNull(Dish::getStoreId)));
        } else {
            query.and(wrapper -> wrapper.eq(Dish::getSourceType, 0)
                    .or().eq(Dish::getOwnerStoreId, storeId)
                    .or(legacy -> legacy.isNull(Dish::getSourceType).eq(Dish::getStoreId, storeId)));
        }
        query.orderByAsc(Dish::getSortOrder).orderByDesc(Dish::getId)
                .last("LIMIT " + candidateLimit);
        List<Dish> candidates = dishMapper.selectList(query);
        Map<Long, StoreDish> storeConfigs = storeId == null || candidates.isEmpty()
                ? Map.of() : storeDishMapper.selectList(new LambdaQueryWrapper<StoreDish>()
                        .eq(StoreDish::getStoreId, storeId)
                        .in(StoreDish::getDishId, candidates.stream().map(Dish::getId).toList())
                        .eq(StoreDish::getDeleted, 0)
                        .and(wrapper -> wrapper.isNull(StoreDish::getListingStatus)
                                .or().ne(StoreDish::getListingStatus, 2)))
                .stream().collect(Collectors.toMap(StoreDish::getDishId, Function.identity(), (a, b) -> a));
        List<MemberPriceDishOptionVO> options = candidates.stream()
                .filter(dish -> storeId == null || storeConfigs.containsKey(dish.getId()))
                .limit(max)
                .map(dish -> memberPriceDishOption(dish, true, null))
                .collect(Collectors.toCollection(ArrayList::new));

        if (includeDishId != null && options.stream().noneMatch(item -> Objects.equals(item.getId(), includeDishId))) {
            Dish included = dishMapper.selectById(includeDishId);
            if (included != null && !Integer.valueOf(1).equals(included.getDeleted())
                    && memberPriceDishVisibleAtStore(included, storeId)) {
                StoreDish config = storeId == null ? null : findStoreDish(storeId, included.getId());
                boolean available = Integer.valueOf(1).equals(included.getMasterStatus())
                        && (storeId == null || (config != null && !Integer.valueOf(2).equals(config.getListingStatus())));
                String reason = !Integer.valueOf(1).equals(included.getMasterStatus())
                        ? "总部已停用" : (storeId != null && config == null ? "未发布到该门店" : "门店已清退");
                options.add(0, memberPriceDishOption(included, available, available ? null : reason));
            }
        }
        return options;
    }

    /** 新增或更新常态会员菜品价。 */
    @Transactional(rollbackFor = Exception.class)
    public MemberDishPrice saveMemberPrice(MemberDishPrice price) {
        if (price == null || price.getDishId() == null || price.getMemberLevelCode() == null
                || price.getMemberPrice() == null || price.getMemberPrice().signum() < 0) {
            throw new BusinessException("菜品、会员等级和非负会员价不能为空");
        }
        if (storeDataScopeService.requiresStoreRestriction() && price.getStoreId() == null) {
            throw new BusinessException("门店账号不能维护全部门店会员价");
        }
        if (price.getStoreId() != null) storeDataScopeService.checkStoreAccess(price.getStoreId());
        if (price.getStartTime() != null && price.getEndTime() != null
                && !price.getEndTime().isAfter(price.getStartTime())) {
            throw new BusinessException("会员价结束时间必须晚于开始时间");
        }
        validateMemberPriceDish(price);
        LocalDateTime now = LocalDateTime.now();
        if (price.getId() == null) {
            price.setDeleted(0).setStatus(price.getStatus() == null ? 1 : price.getStatus())
                    .setPriority(defaultInt(price.getPriority(), 0)).setCreatedAt(now).setUpdatedAt(now);
            memberPriceMapper.insert(price);
        } else {
            MemberDishPrice existing = memberPriceMapper.selectById(price.getId());
            if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
                throw new BusinessException("会员价不存在");
            }
            if (storeDataScopeService.requiresStoreRestriction() && existing.getStoreId() == null) {
                throw new BusinessException("门店账号不能修改总部会员价");
            }
            if (existing.getStoreId() != null) storeDataScopeService.checkStoreAccess(existing.getStoreId());
            price.setDeleted(0).setUpdatedAt(now).setCreatedAt(existing.getCreatedAt());
            memberPriceMapper.updateById(price);
        }
        return price;
    }

    /** 逻辑删除常态会员菜品价。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMemberPrice(Long id) {
        MemberDishPrice price = memberPriceMapper.selectById(id);
        if (price == null) return;
        if (storeDataScopeService.requiresStoreRestriction() && price.getStoreId() == null) {
            throw new BusinessException("门店账号不能删除总部会员价");
        }
        if (price.getStoreId() != null) storeDataScopeService.checkStoreAccess(price.getStoreId());
        price.setDeleted(1).setStatus(0).setUpdatedAt(LocalDateTime.now());
        memberPriceMapper.updateById(price);
    }

    /** 只更新会员菜品价状态，保留其余定价字段。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberPriceStatus(Long id, Integer status) {
        validateStatus(status);
        MemberDishPrice price = memberPriceMapper.selectById(id);
        if (price == null || Integer.valueOf(1).equals(price.getDeleted())) {
            throw new BusinessException("会员价不存在");
        }
        if (storeDataScopeService.requiresStoreRestriction() && price.getStoreId() == null) {
            throw new BusinessException("门店账号不能修改总部会员价");
        }
        if (price.getStoreId() != null) storeDataScopeService.checkStoreAccess(price.getStoreId());
        price.setStatus(status).setUpdatedAt(LocalDateTime.now());
        memberPriceMapper.updateById(price);
    }

    /** 使用服务端可信数据计算购物车各行及整单应付金额。 */
    public PricingQuoteVO quote(PricingQuoteRequest request) {
        if (request == null || request.getStoreId() == null) throw new BusinessException("请选择定价门店");
        if (request.getItems() == null || request.getItems().isEmpty()) throw new BusinessException("试价商品不能为空");
        storeDataScopeService.checkStoreAccess(request.getStoreId());
        Integer memberLevel = resolveMemberLevel(request);
        LocalDateTime now = LocalDateTime.now();
        List<PricingCampaign> campaigns = activeCampaigns(now);
        Map<Long, PricingRule> rules = campaigns.isEmpty() ? Map.of()
                : childrenByCampaign(ruleMapper.selectList(new LambdaQueryWrapper<PricingRule>()
                .in(PricingRule::getCampaignId, ids(campaigns))), PricingRule::getCampaignId);
        Map<Long, List<PricingCampaignScope>> scopes = groupedScopes(campaigns);
        Map<Long, List<PricingCampaignAudience>> audiences = groupedAudiences(campaigns);
        Map<Long, Set<Long>> groupStores = groupedStoreIds(scopes);

        PricingQuoteVO result = new PricingQuoteVO().setStoreId(request.getStoreId())
                .setUserId(request.getUserId()).setMemberLevelCode(memberLevel)
                .setListAmount(ZERO).setCampaignDiscountAmount(ZERO).setPayableAmount(ZERO);
        for (PricingQuoteRequest.Item item : request.getItems()) {
            result.getLines().add(priceLine(request, item, memberLevel, now, campaigns, rules, scopes,
                    audiences, groupStores));
        }
        applyOrderAmountCampaigns(result, request, memberLevel, campaigns, rules, scopes, audiences, groupStores);
        BigDecimal listAmount = result.getLines().stream().map(PricingQuoteVO.Line::getLineListAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payable = result.getLines().stream().map(PricingQuoteVO.Line::getLinePayableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return result.setListAmount(listAmount).setPayableAmount(payable)
                .setCampaignDiscountAmount(listAmount.subtract(payable).max(BigDecimal.ZERO));
    }

    /** 菜单列表使用数量1、匿名人群试价；数量活动在购物车试价时才体现。 */
    public void enrichMenuPrice(DishVO dish, Long storeId) {
        if (dish == null || storeId == null || dish.getId() == null) return;
        PricingQuoteRequest request = new PricingQuoteRequest();
        request.setStoreId(storeId);
        PricingQuoteRequest.Item item = new PricingQuoteRequest.Item();
        item.setDishId(dish.getId());
        item.setQuantity(1);
        item.setSpecExtraPrice(BigDecimal.ZERO);
        request.setItems(List.of(item));
        PricingQuoteVO.Line line = quote(request).getLines().getFirst();
        dish.setCampaignPrice(line.getUnitSellingPrice().compareTo(line.getUnitListPrice()) < 0
                ? line.getUnitSellingPrice() : null);
        dish.setDisplayPrice(line.getUnitSellingPrice());
        dish.setPriceSource(line.getPriceSource());
        PricingQuoteVO.Adjustment first = line.getAdjustments().isEmpty() ? null : line.getAdjustments().getFirst();
        if (first != null) {
            dish.setAppliedCampaignId(first.getCampaignId());
            dish.setAppliedCampaignName(first.getCampaignName());
        }
        dish.setAvailableCampaigns(line.getAvailableCampaigns());
    }

    private PricingQuoteVO.Line priceLine(PricingQuoteRequest request, PricingQuoteRequest.Item item,
                                           Integer memberLevel, LocalDateTime now,
                                           List<PricingCampaign> campaigns, Map<Long, PricingRule> rules,
                                           Map<Long, List<PricingCampaignScope>> scopes,
                                           Map<Long, List<PricingCampaignAudience>> audiences,
                                           Map<Long, Set<Long>> groupStores) {
        if (item.getDishId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BusinessException("试价商品和数量不正确");
        }
        Dish dish = dishMapper.selectById(item.getDishId());
        if (dish == null || Integer.valueOf(1).equals(dish.getDeleted())) throw new BusinessException("菜品不存在");
        StoreDish config = storeDishMapper.selectOne(new LambdaQueryWrapper<StoreDish>()
                .eq(StoreDish::getStoreId, request.getStoreId()).eq(StoreDish::getDishId, dish.getId())
                .eq(StoreDish::getDeleted, 0).last("LIMIT 1"));
        validateDishAvailable(request.getStoreId(), dish, config);
        BigDecimal normal = config != null && config.getSalePriceOverride() != null
                ? config.getSalePriceOverride() : dish.getSalePrice();
        if (normal == null || normal.signum() < 0) throw new BusinessException("菜品【" + dish.getDishName() + "】售价异常");
        BigDecimal extra = item.getSpecExtraPrice() == null ? BigDecimal.ZERO : item.getSpecExtraPrice();
        if (extra.signum() < 0) throw new BusinessException("规格加价不能小于0");
        BigDecimal listUnit = money(normal.add(extra));
        BigDecimal currentUnit = listUnit;
        String priceSource = config != null && config.getSalePriceOverride() != null
                ? "STORE_OVERRIDE" : "HEADQUARTERS";
        List<PricingQuoteVO.Adjustment> adjustments = new ArrayList<>();

        DirectPriceCandidate best = memberCandidate(request.getStoreId(), dish.getId(), memberLevel, now, extra);
        for (PricingCampaign campaign : campaigns) {
            PricingRule rule = rules.get(campaign.getId());
            if (rule == null || rule.getConditionType() != ConditionType.ALWAYS) continue;
            if (!matchesScope(scopes.get(campaign.getId()), request.getStoreId(), dish, config, groupStores)
                    || !matchesAudience(audiences.get(campaign.getId()), request.getUserId(), memberLevel)) continue;
            BigDecimal candidate = applyUnitAction(listUnit, extra, rule);
            if (candidate != null && (best == null || candidate.compareTo(best.price()) < 0)) {
                best = new DirectPriceCandidate(candidate, campaign.getId(), campaign.getCampaignName(),
                        "PROMOTION", adjustmentType(campaign), snapshot(campaign, rule));
            }
        }
        if (best != null && best.price().compareTo(currentUnit) < 0) {
            BigDecimal before = currentUnit.multiply(BigDecimal.valueOf(item.getQuantity()));
            currentUnit = best.price();
            BigDecimal after = currentUnit.multiply(BigDecimal.valueOf(item.getQuantity()));
            adjustments.add(adjustment(best.campaignId(), best.name(), best.adjustmentType(), before,
                    before.subtract(after), after, best.snapshot()));
            priceSource = best.priceSource();
        }

        BigDecimal linePayable = currentUnit.multiply(BigDecimal.valueOf(item.getQuantity()));
        Set<String> appliedGroups = new HashSet<>();
        for (PricingCampaign campaign : campaigns) {
            PricingRule rule = rules.get(campaign.getId());
            if (rule == null || rule.getConditionType() != ConditionType.QUANTITY) continue;
            if (!matchesScope(scopes.get(campaign.getId()), request.getStoreId(), dish, config, groupStores)
                    || !matchesAudience(audiences.get(campaign.getId()), request.getUserId(), memberLevel)) continue;
            String group = StringUtils.hasText(campaign.getExclusiveGroup())
                    ? campaign.getExclusiveGroup() : "QUANTITY_PROMOTION";
            if (appliedGroups.contains(group)) continue;
            int minimum = Math.max(1, defaultInt(rule.getMinimumQuantity(), 1));
            int nth = Math.max(1, defaultInt(rule.getNthQuantity(), minimum));
            if (item.getQuantity() < minimum || item.getQuantity() < nth) continue;
            int discountedCount = rule.getRepeatMode() == RepeatMode.EVERY_GROUP
                    ? item.getQuantity() / minimum : 1;
            BigDecimal discountedUnit = applyUnitAction(currentUnit, extra, rule);
            if (discountedUnit == null || discountedUnit.compareTo(currentUnit) >= 0) continue;
            BigDecimal discount = currentUnit.subtract(discountedUnit)
                    .multiply(BigDecimal.valueOf(discountedCount));
            if (campaign.getStackMode() == StackMode.EXCLUSIVE && !adjustments.isEmpty()) {
                BigDecimal exclusiveTotal = listUnit.multiply(BigDecimal.valueOf(item.getQuantity()))
                        .subtract(listUnit.subtract(applyUnitAction(listUnit, extra, rule))
                                .multiply(BigDecimal.valueOf(discountedCount)));
                if (exclusiveTotal.compareTo(linePayable) >= 0) continue;
                adjustments.clear();
                currentUnit = listUnit;
                linePayable = listUnit.multiply(BigDecimal.valueOf(item.getQuantity()));
                discount = linePayable.subtract(exclusiveTotal);
                priceSource = "PROMOTION";
            }
            BigDecimal before = linePayable;
            linePayable = money(linePayable.subtract(discount).max(BigDecimal.ZERO));
            adjustments.add(adjustment(campaign.getId(), campaign.getCampaignName(), AdjustmentType.NTH_ITEM_DISCOUNT,
                    before, before.subtract(linePayable), linePayable, snapshot(campaign, rule)));
            appliedGroups.add(group);
        }
        BigDecimal lineList = money(listUnit.multiply(BigDecimal.valueOf(item.getQuantity())));
        linePayable = money(linePayable);
        BigDecimal unitCost = config != null && config.getCostPriceOverride() != null
                ? config.getCostPriceOverride() : dish.getCostPrice();
        List<PricingQuoteVO.CampaignHint> availableCampaigns = campaigns.stream()
                .filter(campaign -> matchesScope(scopes.get(campaign.getId()), request.getStoreId(), dish,
                        config, groupStores))
                .filter(campaign -> matchesAudience(audiences.get(campaign.getId()), request.getUserId(), memberLevel))
                .map(campaign -> campaignHint(campaign, rules.get(campaign.getId())))
                .filter(Objects::nonNull)
                .toList();
        return new PricingQuoteVO.Line().setLineKey(item.getLineKey()).setDishId(dish.getId())
                .setDishName(dish.getDishName()).setQuantity(item.getQuantity()).setUnitListPrice(listUnit)
                .setUnitSellingPrice(money(linePayable.divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP)))
                .setUnitCost(unitCost == null ? null : money(unitCost))
                .setLineListAmount(lineList).setLineDiscountAmount(lineList.subtract(linePayable))
                .setLinePayableAmount(linePayable).setPriceSource(priceSource).setAdjustments(adjustments)
                .setAvailableCampaigns(availableCampaigns);
    }

    /**
     * 订单金额活动在所有单品价、会员价和数量活动之后执行，并按行金额比例分摊，
     * 这样订单项和优惠快照在退款、报表中仍可独立核对。
     */
    private void applyOrderAmountCampaigns(PricingQuoteVO result, PricingQuoteRequest request,
                                            Integer memberLevel, List<PricingCampaign> campaigns,
                                            Map<Long, PricingRule> rules,
                                            Map<Long, List<PricingCampaignScope>> scopes,
                                            Map<Long, List<PricingCampaignAudience>> audiences,
                                            Map<Long, Set<Long>> groupStores) {
        Set<String> appliedGroups = new HashSet<>();
        for (PricingCampaign campaign : campaigns) {
            PricingRule rule = rules.get(campaign.getId());
            if (rule == null || rule.getConditionType() != ConditionType.ORDER_AMOUNT
                    || !matchesAudience(audiences.get(campaign.getId()), request.getUserId(), memberLevel)) {
                continue;
            }
            String group = StringUtils.hasText(campaign.getExclusiveGroup())
                    ? campaign.getExclusiveGroup() : "ORDER_PROMOTION";
            if (appliedGroups.contains(group)) continue;

            List<PricingQuoteVO.Line> eligible = result.getLines().stream().filter(line -> {
                Dish dish = dishMapper.selectById(line.getDishId());
                StoreDish config = dish == null ? null : findStoreDish(request.getStoreId(), dish.getId());
                return dish != null && matchesScope(scopes.get(campaign.getId()), request.getStoreId(), dish,
                        config, groupStores);
            }).toList();
            if (eligible.isEmpty()) continue;

            boolean exclusive = campaign.getStackMode() == StackMode.EXCLUSIVE;
            BigDecimal basis = eligible.stream().map(exclusive
                            ? PricingQuoteVO.Line::getLineListAmount : PricingQuoteVO.Line::getLinePayableAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal thresholdBasis = eligible.stream().map(PricingQuoteVO.Line::getLinePayableAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (rule.getMinimumAmount() != null && thresholdBasis.compareTo(rule.getMinimumAmount()) < 0) continue;
            BigDecimal after = applyUnitAction(basis, BigDecimal.ZERO, rule);
            if (after == null || after.compareTo(basis) >= 0) continue;

            BigDecimal currentPayable = eligible.stream().map(PricingQuoteVO.Line::getLinePayableAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (exclusive && after.compareTo(currentPayable) >= 0) continue;
            BigDecimal totalDiscount = money(basis.subtract(after));
            distributeOrderDiscount(eligible, basis, totalDiscount, campaign, rule, exclusive);
            appliedGroups.add(group);
        }
    }

    private void distributeOrderDiscount(List<PricingQuoteVO.Line> lines, BigDecimal basis,
                                         BigDecimal totalDiscount, PricingCampaign campaign,
                                         PricingRule rule, boolean exclusive) {
        BigDecimal remaining = totalDiscount;
        for (int i = 0; i < lines.size(); i++) {
            PricingQuoteVO.Line line = lines.get(i);
            BigDecimal lineBasis = exclusive ? line.getLineListAmount() : line.getLinePayableAmount();
            BigDecimal share = i == lines.size() - 1 ? remaining
                    : money(totalDiscount.multiply(lineBasis).divide(basis, 8, RoundingMode.HALF_UP));
            share = share.min(lineBasis).max(BigDecimal.ZERO);
            BigDecimal before = exclusive ? line.getLineListAmount() : line.getLinePayableAmount();
            BigDecimal lineAfter = money(before.subtract(share).max(BigDecimal.ZERO));
            if (exclusive) line.getAdjustments().clear();
            line.getAdjustments().add(adjustment(campaign.getId(), campaign.getCampaignName(),
                    AdjustmentType.ORDER_DISCOUNT, before, before.subtract(lineAfter), lineAfter,
                    snapshot(campaign, rule)));
            line.setLinePayableAmount(lineAfter)
                    .setLineDiscountAmount(line.getLineListAmount().subtract(lineAfter))
                    .setUnitSellingPrice(money(lineAfter.divide(BigDecimal.valueOf(line.getQuantity()),
                            2, RoundingMode.HALF_UP)))
                    .setPriceSource("PROMOTION");
            remaining = remaining.subtract(share);
        }
    }

    private DirectPriceCandidate memberCandidate(Long storeId, Long dishId, Integer level,
                                                  LocalDateTime now, BigDecimal extra) {
        if (level == null) return null;
        List<MemberDishPrice> prices = memberPriceMapper.selectList(new LambdaQueryWrapper<MemberDishPrice>()
                .eq(MemberDishPrice::getDishId, dishId).eq(MemberDishPrice::getMemberLevelCode, level)
                .eq(MemberDishPrice::getStatus, 1).eq(MemberDishPrice::getDeleted, 0)
                .and(w -> w.isNull(MemberDishPrice::getStoreId).or().eq(MemberDishPrice::getStoreId, storeId))
                .and(w -> w.isNull(MemberDishPrice::getStartTime).or().le(MemberDishPrice::getStartTime, now))
                .and(w -> w.isNull(MemberDishPrice::getEndTime).or().gt(MemberDishPrice::getEndTime, now))
                .orderByDesc(MemberDishPrice::getStoreId)
                .orderByDesc(MemberDishPrice::getPriority)
                .orderByDesc(MemberDishPrice::getId));
        if (prices.isEmpty()) return null;
        // 门店专属配置优先于总部配置，同一层级再按 priority 和 ID 取首条。
        MemberDishPrice value = prices.getFirst();
        return new DirectPriceCandidate(money(value.getMemberPrice().add(extra)), null,
                "会员等级价", "MEMBER_PRICE", AdjustmentType.MEMBER_PRICE, json(Map.of("memberPriceId", value.getId(),
                "memberLevelCode", level, "memberPrice", value.getMemberPrice())));
    }

    private BigDecimal applyUnitAction(BigDecimal basis, BigDecimal extra, PricingRule rule) {
        if (rule.getActionType() == null) return null;
        BigDecimal value = rule.getActionValue() == null ? BigDecimal.ZERO : rule.getActionValue();
        return switch (rule.getActionType()) {
            case FIXED_PRICE -> money(value.add(extra));
            case DISCOUNT_RATE -> money(basis.multiply(value));
            case REDUCE_AMOUNT -> money(basis.subtract(value).max(BigDecimal.ZERO));
            case FREE_ITEM -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private List<PricingCampaign> activeCampaigns(LocalDateTime now) {
        return campaignMapper.selectList(new LambdaQueryWrapper<PricingCampaign>()
                .eq(PricingCampaign::getStatus, 1).eq(PricingCampaign::getDeleted, 0)
                .and(w -> w.isNull(PricingCampaign::getStartTime).or().le(PricingCampaign::getStartTime, now))
                .and(w -> w.isNull(PricingCampaign::getEndTime).or().gt(PricingCampaign::getEndTime, now))
                .orderByDesc(PricingCampaign::getPriority).orderByAsc(PricingCampaign::getId));
    }

    private Map<Long, List<PricingCampaignScope>> groupedScopes(List<PricingCampaign> campaigns) {
        if (campaigns.isEmpty()) return Map.of();
        return scopeMapper.selectList(new LambdaQueryWrapper<PricingCampaignScope>()
                        .in(PricingCampaignScope::getCampaignId, ids(campaigns))).stream()
                .collect(Collectors.groupingBy(PricingCampaignScope::getCampaignId));
    }

    private Map<Long, List<PricingCampaignAudience>> groupedAudiences(List<PricingCampaign> campaigns) {
        if (campaigns.isEmpty()) return Map.of();
        return audienceMapper.selectList(new LambdaQueryWrapper<PricingCampaignAudience>()
                        .in(PricingCampaignAudience::getCampaignId, ids(campaigns))).stream()
                .collect(Collectors.groupingBy(PricingCampaignAudience::getCampaignId));
    }

    /** 一次试价内缓存启用分组的成员，避免逐活动、逐菜品重复查询。 */
    private Map<Long, Set<Long>> groupedStoreIds(Map<Long, List<PricingCampaignScope>> scopes) {
        Set<Long> groupIds = scopes.values().stream().flatMap(Collection::stream)
                .map(PricingCampaignScope::getStoreGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (groupIds.isEmpty()) return Map.of();
        Set<Long> enabledIds = storeGroupMapper.selectList(new LambdaQueryWrapper<StoreGroup>()
                        .in(StoreGroup::getId, groupIds).eq(StoreGroup::getStatus, 1).eq(StoreGroup::getDeleted, 0))
                .stream().map(StoreGroup::getId).collect(Collectors.toSet());
        if (enabledIds.isEmpty()) return Map.of();
        return storeGroupMemberMapper.selectList(new LambdaQueryWrapper<StoreGroupMember>()
                        .in(StoreGroupMember::getGroupId, enabledIds)).stream()
                .collect(Collectors.groupingBy(StoreGroupMember::getGroupId,
                        Collectors.mapping(StoreGroupMember::getStoreId, Collectors.toSet())));
    }

    private List<Long> visibleCampaignIds() {
        if (!storeDataScopeService.requiresStoreRestriction()) return List.of();
        List<Long> allowed = storeDataScopeService.getAllowedStoreIds();
        if (allowed.isEmpty()) return List.of();
        Set<Long> groups = storeGroupMemberMapper.selectList(new LambdaQueryWrapper<StoreGroupMember>()
                        .in(StoreGroupMember::getStoreId, allowed)).stream()
                .map(StoreGroupMember::getGroupId).collect(Collectors.toSet());
        return scopeMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(scope -> (scope.getStoreId() == null && scope.getStoreGroupId() == null)
                        || allowed.contains(scope.getStoreId()) || groups.contains(scope.getStoreGroupId()))
                .map(PricingCampaignScope::getCampaignId).distinct().toList();
    }

    private void ensureCampaignVisible(List<PricingCampaignScope> scopes) {
        if (!storeDataScopeService.requiresStoreRestriction()) return;
        List<Long> allowed = storeDataScopeService.getAllowedStoreIds();
        Set<Long> groups = storeGroupMemberMapper.selectList(new LambdaQueryWrapper<StoreGroupMember>()
                        .in(StoreGroupMember::getStoreId, allowed)).stream()
                .map(StoreGroupMember::getGroupId).collect(Collectors.toSet());
        boolean visible = scopes.stream().anyMatch(scope ->
                (scope.getStoreId() == null && scope.getStoreGroupId() == null)
                        || allowed.contains(scope.getStoreId()) || groups.contains(scope.getStoreGroupId()));
        if (!visible) throw new SecurityException("无权访问该定价活动");
    }

    private void ensureCampaignManageable(Long campaignId) {
        if (!storeDataScopeService.requiresStoreRestriction()) return;
        List<PricingCampaignScope> scopes = scopeMapper.selectList(new LambdaQueryWrapper<PricingCampaignScope>()
                .eq(PricingCampaignScope::getCampaignId, campaignId));
        List<Long> allowed = storeDataScopeService.getAllowedStoreIds();
        if (scopes.isEmpty() || scopes.stream().anyMatch(scope -> scope.getStoreId() == null
                || scope.getStoreGroupId() != null
                || !allowed.contains(scope.getStoreId()))) {
            throw new SecurityException("门店账号不能修改总部或其他门店的定价活动");
        }
    }

    private boolean matchesScope(List<PricingCampaignScope> scopes, Long storeId, Dish dish,
                                 StoreDish config, Map<Long, Set<Long>> groupStores) {
        Long effectiveCategoryId = config != null && config.getCategoryOverrideId() != null
                ? config.getCategoryOverrideId() : dish.getCategoryId();
        return scopes != null && scopes.stream().anyMatch(scope ->
                ((scope.getStoreId() == null && scope.getStoreGroupId() == null)
                        || Objects.equals(scope.getStoreId(), storeId)
                        || (scope.getStoreGroupId() != null
                        && groupStores.getOrDefault(scope.getStoreGroupId(), Set.of()).contains(storeId)))
                        && (scope.getTargetType() == TargetType.ALL
                        || (scope.getTargetType() == TargetType.DISH && Objects.equals(scope.getTargetId(), dish.getId()))
                        || (scope.getTargetType() == TargetType.CATEGORY
                        && Objects.equals(scope.getTargetId(), effectiveCategoryId))));
    }

    private StoreDish findStoreDish(Long storeId, Long dishId) {
        return storeDishMapper.selectOne(new LambdaQueryWrapper<StoreDish>()
                .eq(StoreDish::getStoreId, storeId).eq(StoreDish::getDishId, dishId)
                .eq(StoreDish::getDeleted, 0).last("LIMIT 1"));
    }

    /** 每次保存只核验当前一条菜品，不扫描其它会员价或菜品。 */
    private void validateMemberPriceDish(MemberDishPrice price) {
        Dish dish = dishMapper.selectById(price.getDishId());
        if (dish == null || Integer.valueOf(1).equals(dish.getDeleted())) {
            throw new BusinessException("菜品不存在");
        }
        if (!memberPriceDishVisibleAtStore(dish, price.getStoreId())) {
            throw new BusinessException(price.getStoreId() == null
                    ? "全部门店会员价只能选择总部统一菜品"
                    : "菜品不属于所选门店");
        }
        if (price.getStoreId() != null && findStoreDish(price.getStoreId(), dish.getId()) == null) {
            throw new BusinessException("菜品【" + dish.getDishName() + "】未发布到所选门店");
        }
    }

    private boolean memberPriceDishVisibleAtStore(Dish dish, Long storeId) {
        if (storeId == null) {
            return Integer.valueOf(0).equals(dish.getSourceType())
                    || (dish.getSourceType() == null && dish.getStoreId() == null);
        }
        return Integer.valueOf(0).equals(dish.getSourceType())
                || Objects.equals(dish.getOwnerStoreId(), storeId)
                || (dish.getSourceType() == null && Objects.equals(dish.getStoreId(), storeId));
    }

    private MemberPriceDishOptionVO memberPriceDishOption(Dish dish, boolean available, String reason) {
        return new MemberPriceDishOptionVO().setId(dish.getId()).setDishName(dish.getDishName())
                .setSourceType(dish.getSourceType()).setAvailable(available).setUnavailableReason(reason);
    }

    /** 活动只能作用于当前门店真实可售的菜单项，门店自建菜不能跨店命中。 */
    private void validateDishAvailable(Long storeId, Dish dish, StoreDish config) {
        if (Integer.valueOf(1).equals(dish.getSourceType())
                && !Objects.equals(dish.getOwnerStoreId(), storeId)) {
            throw new BusinessException("菜品【" + dish.getDishName() + "】不属于当前门店");
        }
        if ((Integer.valueOf(0).equals(dish.getSourceType())
                || Integer.valueOf(1).equals(dish.getSourceType())) && config == null) {
            throw new BusinessException("菜品【" + dish.getDishName() + "】未发布到当前门店");
        }
        if (dish.getMasterStatus() != null && !Integer.valueOf(1).equals(dish.getMasterStatus())) {
            throw new BusinessException("菜品【" + dish.getDishName() + "】已被总部停用");
        }
        if (config != null && (!Integer.valueOf(1).equals(config.getListingStatus())
                || Integer.valueOf(1).equals(config.getSoldOut()))) {
            throw new BusinessException("菜品【" + dish.getDishName() + "】在当前门店不可售");
        }
    }

    private boolean matchesAudience(List<PricingCampaignAudience> audiences, Long userId, Integer level) {
        return audiences != null && audiences.stream().anyMatch(audience ->
                audience.getAudienceType() == AudienceType.ALL
                        || (audience.getAudienceType() == AudienceType.MEMBER && userId != null)
                        || (audience.getAudienceType() == AudienceType.MEMBER_LEVEL && level != null
                        && Objects.equals(audience.getAudienceValue(), level)));
    }

    private Integer resolveMemberLevel(PricingQuoteRequest request) {
        if (request.getUserId() == null) return request.getMemberLevelCode();
        MemberPricingDTO member = FeignResultUtil.unwrap(userFeignClient.getMemberPricing(request.getUserId()));
        if (member == null) throw new BusinessException("会员不存在");
        return member.getLevel();
    }

    private void validateCampaign(PricingCampaignSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getCampaignCode())
                || !StringUtils.hasText(request.getCampaignName()) || request.getCampaignType() == null
                || request.getRule() == null) {
            throw new BusinessException("活动编码、名称、类型和定价规则不能为空");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && !request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("活动结束时间必须晚于开始时间");
        }
        PricingRule rule = request.getRule();
        if (rule.getConditionType() == null) {
            throw new BusinessException("不支持的活动条件");
        }
        boolean typeMatchesCondition = switch (request.getCampaignType()) {
            case PROMOTION -> rule.getConditionType() == ConditionType.ALWAYS;
            case QUANTITY_DISCOUNT -> rule.getConditionType() == ConditionType.QUANTITY;
            case ORDER_DISCOUNT -> rule.getConditionType() == ConditionType.ORDER_AMOUNT;
        };
        if (!typeMatchesCondition) {
            throw new BusinessException("活动类型与触发条件不匹配");
        }
        if (rule.getActionType() == null) throw new BusinessException("不支持的优惠动作");
        if (rule.getActionType() == ActionType.DISCOUNT_RATE
                && (rule.getActionValue() == null || rule.getActionValue().signum() < 0
                || rule.getActionValue().compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("折扣率必须在0到1之间");
        }
        if (rule.getActionType() != ActionType.FREE_ITEM
                && (rule.getActionValue() == null || rule.getActionValue().signum() < 0)) {
            throw new BusinessException("优惠值不能小于0");
        }
        if (rule.getConditionType() == ConditionType.QUANTITY
                && (rule.getMinimumQuantity() == null || rule.getMinimumQuantity() < 2
                || rule.getNthQuantity() == null || rule.getNthQuantity() < 2
                || rule.getNthQuantity() > rule.getMinimumQuantity())) {
            throw new BusinessException("数量活动的门槛必须大于等于2，优惠件次不能超过每组门槛");
        }
        if (rule.getConditionType() == ConditionType.QUANTITY
                && rule.getGroupingType() != null && rule.getGroupingType() != GroupingType.SAME_DISH) {
            throw new BusinessException("当前数量活动仅支持按同菜品分组");
        }
        if (rule.getConditionType() == ConditionType.ORDER_AMOUNT
                && (rule.getMinimumAmount() == null || rule.getMinimumAmount().signum() <= 0)) {
            throw new BusinessException("订单金额活动的门槛金额必须大于0");
        }
        List<PricingCampaignScope> scopes = request.getScopes();
        if (storeDataScopeService.requiresStoreRestriction()
                && (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(s -> s.getStoreId() == null
                || s.getStoreGroupId() != null))) {
            throw new BusinessException("门店账号不能创建全门店活动");
        }
        if (scopes != null && scopes.stream().anyMatch(scope ->
                scope.getStoreId() != null && scope.getStoreGroupId() != null)) {
            throw new BusinessException("门店和门店分组不能同时选择");
        }
        if (scopes != null) scopes.stream().map(PricingCampaignScope::getStoreId)
                .filter(Objects::nonNull).distinct().forEach(storeDataScopeService::checkStoreAccess);
        if (scopes != null) {
            Set<Long> groupIds = scopes.stream().map(PricingCampaignScope::getStoreGroupId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!groupIds.isEmpty()) {
                long enabledCount = storeGroupMapper.selectCount(new LambdaQueryWrapper<StoreGroup>()
                        .in(StoreGroup::getId, groupIds).eq(StoreGroup::getStatus, 1)
                        .eq(StoreGroup::getDeleted, 0));
                if (enabledCount != groupIds.size()) throw new BusinessException("门店分组不存在或已停用");
            }
        }
    }

    private PricingCampaign copyCampaign(PricingCampaignSaveRequest source, PricingCampaign target) {
        return target.setCampaignCode(source.getCampaignCode().trim()).setCampaignName(source.getCampaignName().trim())
                .setCampaignType(source.getCampaignType()).setStartTime(source.getStartTime())
                .setEndTime(source.getEndTime()).setPriority(defaultInt(source.getPriority(), 0))
                .setExclusiveGroup(StringUtils.hasText(source.getExclusiveGroup())
                        ? source.getExclusiveGroup() : defaultExclusiveGroup(source.getCampaignType()))
                .setStackMode(source.getStackMode() == null ? StackMode.EXCLUSIVE : source.getStackMode())
                .setStatus(source.getStatus() == null ? 1 : source.getStatus());
    }

    private void ensureCampaignCodeUnique(String code, Long excludingId) {
        Long count = campaignMapper.selectCount(new LambdaQueryWrapper<PricingCampaign>()
                .eq(PricingCampaign::getCampaignCode, code.trim()).eq(PricingCampaign::getDeleted, 0)
                .ne(excludingId != null, PricingCampaign::getId, excludingId));
        if (count > 0) throw new BusinessException("活动编码已存在");
    }

    private PricingQuoteVO.Adjustment adjustment(Long campaignId, String name, AdjustmentType type,
                                                  BigDecimal before, BigDecimal discount,
                                                  BigDecimal after, String snapshot) {
        return new PricingQuoteVO.Adjustment().setCampaignId(campaignId).setCampaignName(name)
                .setAdjustmentType(type).setBeforeAmount(money(before)).setAdjustmentAmount(money(discount))
                .setAfterAmount(money(after)).setDescription(name).setRuleSnapshot(snapshot);
    }

    private String snapshot(PricingCampaign campaign, PricingRule rule) {
        return json(Map.of("campaign", campaign, "rule", rule));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("定价规则快照序列化失败", e);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值只能为启用或停用");
        }
    }

    private List<Long> ids(List<PricingCampaign> campaigns) {
        return campaigns.stream().map(PricingCampaign::getId).toList();
    }

    private <T> Map<Long, T> childrenByCampaign(List<T> values, Function<T, Long> campaignId) {
        return values.stream().collect(Collectors.toMap(campaignId, Function.identity(), (left, right) -> left));
    }

    private AdjustmentType adjustmentType(PricingCampaign campaign) {
        if (campaign.getCampaignType() == null) return AdjustmentType.PROMOTION;
        return switch (campaign.getCampaignType()) {
            case PROMOTION -> AdjustmentType.PROMOTION;
            case QUANTITY_DISCOUNT -> AdjustmentType.NTH_ITEM_DISCOUNT;
            case ORDER_DISCOUNT -> AdjustmentType.ORDER_DISCOUNT;
        };
    }

    private String defaultExclusiveGroup(CampaignType type) {
        if (type == null) return "DIRECT_PRICE";
        return switch (type) {
            case PROMOTION -> "DIRECT_PRICE";
            case QUANTITY_DISCOUNT -> "QUANTITY_PROMOTION";
            case ORDER_DISCOUNT -> "ORDER_PROMOTION";
        };
    }

    private PricingQuoteVO.CampaignHint campaignHint(PricingCampaign campaign, PricingRule rule) {
        if (campaign == null || rule == null) return null;
        String description = switch (rule.getConditionType()) {
            case ALWAYS -> "活动价";
            case QUANTITY -> "满" + defaultInt(rule.getMinimumQuantity(), 1) + "件，第"
                    + defaultInt(rule.getNthQuantity(), defaultInt(rule.getMinimumQuantity(), 1)) + "件优惠";
            case ORDER_AMOUNT -> "满￥" + money(rule.getMinimumAmount() == null
                    ? BigDecimal.ZERO : rule.getMinimumAmount()) + "可享";
        };
        return new PricingQuoteVO.CampaignHint().setCampaignId(campaign.getId())
                .setCampaignName(campaign.getCampaignName()).setCampaignType(campaign.getCampaignType())
                .setDescription(description);
    }

    private record DirectPriceCandidate(BigDecimal price, Long campaignId, String name,
                                        String priceSource, AdjustmentType adjustmentType, String snapshot) {
    }
}
