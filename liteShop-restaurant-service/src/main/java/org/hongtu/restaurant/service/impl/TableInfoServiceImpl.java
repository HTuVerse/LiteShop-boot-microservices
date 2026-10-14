package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.TableInfo;
import org.hongtu.restaurant.DTO.TableReservation;
import org.hongtu.restaurant.DAO.TableInfoMapper;
import org.hongtu.restaurant.DTO.TableSession;
import org.hongtu.restaurant.VO.TableInfoVO;
import org.hongtu.restaurant.VO.TableReservationVO;
import org.hongtu.restaurant.enums.TableReservationStatus;
import org.hongtu.restaurant.enums.TableStatusEnum;
import org.hongtu.restaurant.service.TableInfoService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.restaurant.service.TableReservationService;
import org.hongtu.restaurant.security.TableSessionTokenService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.utils.BeanCopyUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hongtu.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <p>
 * 餐桌信息表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
public class TableInfoServiceImpl extends ServiceImpl<TableInfoMapper, TableInfo> implements TableInfoService {

    @Resource
    private StoreInfoMapper storeInfoMapper;
    @Resource
    private TableSessionService tableSessionService;
    @Resource
    private StoreDataScopeService storeDataScopeService;
    @Resource
    private TableReservationService tableReservationService;
    @Resource
    private TableSessionTokenService tableSessionTokenService;

    @Override
    public TableInfoVO getTableDetail(Long id) {
        TableInfo tableInfo = this.getById(id);
        if (tableInfo == null) {
            return null;
        }
        TableInfoVO tableInfoVO = BeanCopyUtils.copyBean(tableInfo, TableInfoVO.class);
        tableInfoVO.setScanToken(issueScanToken(tableInfo));
        StoreInfo storeInfo = storeInfoMapper.selectById(tableInfo.getStoreId());
        if (storeInfo != null) {
            tableInfoVO.setStoreName(storeInfo.getStoreName());
        }
        fillNextReservation(tableInfoVO, tableReservationService.findNextByTableIds(List.of(id)).get(id));
        return tableInfoVO;
    }

    @Override
    public boolean addTable(TableInfo tableInfo) {
        storeDataScopeService.checkStoreAccess(tableInfo.getStoreId());
        if (tableInfo.getStatus() == TableStatusEnum.IN_USE) throw new BusinessException(400,"请创建桌位后通过开桌进入使用中");
        // 设置创建时间和更新时间
        tableInfo.setCreatedAt(LocalDateTime.now());
        tableInfo.setUpdatedAt(LocalDateTime.now());

        // 默认状态为空闲
        if (tableInfo.getStatus() == null) {
            tableInfo.setStatus(TableStatusEnum.IDLE);
        }

        return this.save(tableInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTable(TableInfo tableInfo) {
        TableInfo existing = getOne(new LambdaQueryWrapper<TableInfo>().eq(TableInfo::getId, tableInfo.getId()).last("FOR UPDATE"));
        if (existing == null) throw new BusinessException(404,"桌位不存在或无权操作");
        org.hongtu.security.OperationPermission.requireChange("restaurant:table:status", existing.getStatus(), tableInfo.getStatus());
        storeDataScopeService.checkStoreAccess(existing.getStoreId());
        storeDataScopeService.checkStoreAccess(tableInfo.getStoreId());
        boolean active = tableSessionService.getActiveSessionByTableId(existing.getId()) != null || existing.getStatus()==TableStatusEnum.IN_USE;
        if (active && (!java.util.Objects.equals(existing.getStoreId(),tableInfo.getStoreId())
                || (tableInfo.getStatus()!=null && tableInfo.getStatus()!=existing.getStatus())))
            throw new BusinessException(409,"用餐中的桌位不能更换门店或更改状态");
        if (tableInfo.getStatus()==TableStatusEnum.IN_USE && existing.getStatus()!=TableStatusEnum.IN_USE)
            throw new BusinessException(400,"请通过开桌进入使用中");
        // 设置更新时间
        tableInfo.setUpdatedAt(LocalDateTime.now());
        return this.updateById(tableInfo);
    }

    @Override
    public boolean deleteTable(Long id) {
        return this.removeById(id);
    }

    @Override
    public boolean batchDeleteTable(List<Long> ids) {
        return this.removeByIds(ids);
    }

    @Override
    public Page<TableInfoVO> getTablePage(Long storeId, String tableNumber, String tableName, Integer status, Page<TableInfo> page) {
        LambdaQueryWrapper<TableInfo> queryWrapper = new LambdaQueryWrapper<>();
        // 门店ID条件
        queryWrapper.eq(storeId != null, TableInfo::getStoreId, storeId);
        // 桌位编号模糊查询
        queryWrapper.like(StringUtils.hasText(tableNumber), TableInfo::getTableNumber, tableNumber);
        // 桌位名称模糊查询
        queryWrapper.like(StringUtils.hasText(tableName), TableInfo::getTableName, tableName);
        // 状态条件
        queryWrapper.eq(status != null, TableInfo::getStatus, status);
        // 按创建时间降序排序
        queryWrapper.orderByDesc(TableInfo::getCreatedAt);
        
        // 查询桌位分页数据
        Page<TableInfo> tableInfoPage = this.page(page, queryWrapper);
        
        // 转换为 VO 并填充门店名称
        Page<TableInfoVO> voPage = new Page<>(tableInfoPage.getCurrent(), tableInfoPage.getSize(), tableInfoPage.getTotal());
        List<TableInfoVO> voList = new ArrayList<>();
        
        if (!tableInfoPage.getRecords().isEmpty()) {
            // 获取所有门店ID
            List<Long> storeIds = tableInfoPage.getRecords().stream()
                    .map(TableInfo::getStoreId)
                    .distinct()
                    .collect(Collectors.toList());
            
            // 批量查询门店信息
            Map<Long, String> storeNameMap = storeInfoMapper.selectByIds(storeIds).stream()
                    .collect(Collectors.toMap(StoreInfo::getId, StoreInfo::getStoreName));
            
            Map<Long, TableReservation> nextReservations = tableReservationService.findNextByTableIds(
                    tableInfoPage.getRecords().stream().map(TableInfo::getId).toList());
            List<Long> tableIds = tableInfoPage.getRecords().stream().map(TableInfo::getId).toList();
            Map<Long, TableSession> activeSessions = tableSessionService.list(
                            new LambdaQueryWrapper<TableSession>()
                                    .in(TableSession::getTableId, tableIds)
                                    .eq(TableSession::getStatus, org.hongtu.restaurant.enums.SessionStatusEnum.IN_PROGRESS)
                                    .orderByDesc(TableSession::getStartTime))
                    .stream()
                    .collect(Collectors.toMap(TableSession::getTableId, session -> session,
                            (first, ignored) -> first));
            // 组装 VO 列表
            voList = tableInfoPage.getRecords().stream().map(tableInfo -> {
                TableInfoVO vo = BeanCopyUtils.copyBean(tableInfo, TableInfoVO.class);
                vo.setScanToken(issueScanToken(tableInfo));
                vo.setStoreName(storeNameMap.get(tableInfo.getStoreId()));
                TableSession activeSession = activeSessions.get(tableInfo.getId());
                if (activeSession != null) {
                    vo.setSessionNo(activeSession.getSessionNo());
                    // 计算用餐时长
                    if (activeSession.getStartTime() != null) {
                        LocalDateTime endTime = activeSession.getEndTime() != null ? activeSession.getEndTime() : LocalDateTime.now();
                        Duration duration = Duration.between(activeSession.getStartTime(), endTime);
                        vo.setDurationMinutes(duration.toMinutes());
                    }
                }
                fillNextReservation(vo, nextReservations.get(tableInfo.getId()));
                return vo;
            }).collect(Collectors.toList());
        }
        
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTableStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 3)) {
            throw new BusinessException(400, "只能手动设置空闲或停用；预约请使用预约管理，使用中请通过开桌进入");
        }
        TableInfo table = getOne(new LambdaQueryWrapper<TableInfo>().eq(TableInfo::getId, id).last("FOR UPDATE"));
        if (table == null) throw new BusinessException(404, "桌位不存在或无权操作");
        storeDataScopeService.checkStoreAccess(table.getStoreId());
        if (table.getStatus() == TableStatusEnum.IN_USE || tableSessionService.getActiveSessionByTableId(id) != null) {
            throw new BusinessException(409, "桌位正在用餐，请结账并结束会话后调整状态");
        }
        LambdaUpdateWrapper<TableInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TableInfo::getId, id)
                .set(TableInfo::getStatus, status)
                .set(TableInfo::getUpdatedAt, LocalDateTime.now());
        return this.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rotateScanToken(Long id) {
        TableInfo table = getOne(new LambdaQueryWrapper<TableInfo>().eq(TableInfo::getId, id).last("FOR UPDATE"));
        if (table == null) {
            throw new BusinessException(404, "桌位不存在或无权操作");
        }
        storeDataScopeService.checkStoreAccess(table.getStoreId());
        table.setScanTokenId(newScanTokenId());
        table.setUpdatedAt(LocalDateTime.now());
        this.updateById(table);
        return tableSessionTokenService.issueTableToken(
                TenantContext.getTenantId(), table.getStoreId(), table.getId(), table.getScanTokenId());
    }

    /**
     * 保证桌台有可吊销的票据编号，并签发当前有效的扫码凭据。
     */
    private String issueScanToken(TableInfo tableInfo) {
        if (!StringUtils.hasText(tableInfo.getScanTokenId())) {
            tableInfo.setScanTokenId(newScanTokenId());
            tableInfo.setUpdatedAt(LocalDateTime.now());
            this.updateById(tableInfo);
        }
        return tableSessionTokenService.issueTableToken(
                TenantContext.getTenantId(), tableInfo.getStoreId(), tableInfo.getId(), tableInfo.getScanTokenId());
    }

    private String newScanTokenId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void fillNextReservation(TableInfoVO vo, TableReservation reservation) {
        if (reservation == null) return;
        TableReservationVO reservationVO = BeanCopyUtils.copyBean(reservation, TableReservationVO.class);
        reservationVO.setStatusDesc(TableReservationStatus.description(reservation.getStatus()));
        vo.setNextReservation(reservationVO);
    }

}
