package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.hongtu.restaurant.DAO.StoreGroupMapper;
import org.hongtu.restaurant.DAO.StoreGroupMemberMapper;
import org.hongtu.restaurant.DTO.StoreGroup;
import org.hongtu.restaurant.DTO.StoreGroupMember;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.VO.StoreGroupSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 门店分组服务，维护分组及其成员并提供批量发布范围。
 */
@Service
public class StoreGroupService {

    @Resource
    private StoreGroupMapper groupMapper;
    @Resource
    private StoreGroupMemberMapper memberMapper;
    @Resource
    private StoreInfoService storeInfoService;
    @Resource
    private StoreDataScopeService storeDataScopeService;

    /** 查询当前商户的全部有效门店分组。 */
    public List<StoreGroup> listGroups() {
        requireHeadquarters();
        List<StoreGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<StoreGroup>()
                .eq(StoreGroup::getDeleted, 0)
                .orderByAsc(StoreGroup::getGroupName));
        groups.forEach(this::fillMembers);
        return groups;
    }

    /** 新增或更新门店分组，成员关系采用全量替换。 */
    @Transactional(rollbackFor = Exception.class)
    public StoreGroup saveGroup(StoreGroupSaveRequest request) {
        requireHeadquarters();
        if (request == null || !StringUtils.hasText(request.getGroupName())
                || !StringUtils.hasText(request.getGroupCode())) {
            throw new IllegalArgumentException("分组名称和编码不能为空");
        }
        List<Long> storeIds = request.getStoreIds() == null ? Collections.emptyList()
                : request.getStoreIds().stream().filter(Objects::nonNull).distinct().toList();
        List<StoreInfo> stores = storeInfoService.listByIds(storeIds);
        if (stores.size() != storeIds.size()) {
            throw new IllegalArgumentException("分组包含不存在或不属于当前商户的门店");
        }
        Long duplicate = groupMapper.selectCount(new LambdaQueryWrapper<StoreGroup>()
                .eq(StoreGroup::getGroupCode, request.getGroupCode().trim())
                .eq(StoreGroup::getDeleted, 0)
                .ne(request.getId() != null, StoreGroup::getId, request.getId()));
        if (duplicate > 0) throw new IllegalArgumentException("门店分组编码已存在");

        LocalDateTime now = LocalDateTime.now();
        StoreGroup group = request.getId() == null ? new StoreGroup() : groupMapper.selectById(request.getId());
        if (group == null || Integer.valueOf(1).equals(group.getDeleted())) {
            throw new IllegalArgumentException("门店分组不存在");
        }
        group.setGroupCode(request.getGroupCode().trim())
                .setGroupName(request.getGroupName().trim())
                .setDescription(request.getDescription())
                .setStatus(request.getStatus() == null ? 1 : request.getStatus())
                .setDeleted(0)
                .setUpdatedAt(now);
        if (request.getId() == null) {
            group.setId(IdWorker.getId()).setCreatedAt(now);
            groupMapper.insert(group);
        } else {
            groupMapper.updateById(group);
            memberMapper.delete(new LambdaQueryWrapper<StoreGroupMember>()
                    .eq(StoreGroupMember::getGroupId, group.getId()));
        }
        for (Long storeId : storeIds) {
            memberMapper.insert(new StoreGroupMember()
                    .setGroupId(group.getId()).setStoreId(storeId).setCreatedAt(now));
        }
        group.setStoreIds(storeIds).setMemberCount(storeIds.size());
        return group;
    }

    /** 逻辑删除分组，同时删除成员关系。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteGroup(Long id) {
        requireHeadquarters();
        if (id == null) return false;
        int updated = groupMapper.update(null, new LambdaUpdateWrapper<StoreGroup>()
                .eq(StoreGroup::getId, id).eq(StoreGroup::getDeleted, 0)
                .set(StoreGroup::getDeleted, 1).set(StoreGroup::getUpdatedAt, LocalDateTime.now()));
        if (updated > 0) {
            memberMapper.delete(new LambdaQueryWrapper<StoreGroupMember>()
                    .eq(StoreGroupMember::getGroupId, id));
        }
        return updated > 0;
    }

    /** 获取启用分组内的门店ID。 */
    public List<Long> resolveEnabledGroupStores(Long groupId) {
        requireHeadquarters();
        StoreGroup group = groupMapper.selectById(groupId);
        if (group == null || !Integer.valueOf(1).equals(group.getStatus())
                || Integer.valueOf(1).equals(group.getDeleted())) {
            throw new IllegalArgumentException("门店分组不存在或已停用");
        }
        return memberMapper.selectList(new LambdaQueryWrapper<StoreGroupMember>()
                        .eq(StoreGroupMember::getGroupId, groupId))
                .stream().map(StoreGroupMember::getStoreId).distinct().toList();
    }

    private void fillMembers(StoreGroup group) {
        List<Long> ids = memberMapper.selectList(new LambdaQueryWrapper<StoreGroupMember>()
                        .eq(StoreGroupMember::getGroupId, group.getId()))
                .stream().map(StoreGroupMember::getStoreId).toList();
        group.setStoreIds(ids).setMemberCount(ids.size());
    }

    private void requireHeadquarters() {
        if (storeDataScopeService.requiresStoreRestriction()) {
            throw new SecurityException("门店账号不能维护门店分组");
        }
    }
}
