package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DAO.TableInfoMapper;
import org.hongtu.restaurant.DAO.TableReservationMapper;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.TableInfo;
import org.hongtu.restaurant.DTO.TableReservation;
import org.hongtu.restaurant.DTO.TableReservationRequest;
import org.hongtu.restaurant.VO.TableReservationVO;
import org.hongtu.restaurant.enums.TableReservationStatus;
import org.hongtu.restaurant.enums.TableStatusEnum;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.TableReservationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TableReservationServiceImpl extends ServiceImpl<TableReservationMapper, TableReservation>
        implements TableReservationService {

    @Resource private TableInfoMapper tableInfoMapper;
    @Resource private StoreInfoMapper storeInfoMapper;
    @Resource private StoreDataScopeService storeDataScopeService;

    @Override
    public Page<TableReservationVO> getPage(Long storeId, Long tableId, String phone, Integer status, String statusGroup,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            Integer currentPage, Integer pageSize) {
        if (storeId != null) storeDataScopeService.checkStoreAccess(storeId);
        LambdaQueryWrapper<TableReservation> query = new LambdaQueryWrapper<TableReservation>()
                .eq(storeId != null, TableReservation::getStoreId, storeId)
                .eq(tableId != null, TableReservation::getTableId, tableId)
                .like(StringUtils.hasText(phone), TableReservation::getReservationPhone,
                        StringUtils.hasText(phone) ? phone.trim() : null)
                .eq(status != null, TableReservation::getStatus, status)
                .ge(startTime != null, TableReservation::getExpectedArrivalTime, startTime)
                .le(endTime != null, TableReservation::getExpectedArrivalTime, endTime);
        if (status == null && "active".equals(statusGroup)) {
            query.in(TableReservation::getStatus, TableReservationStatus.PENDING, TableReservationStatus.ARRIVED);
        } else if (status == null && "history".equals(statusGroup)) {
            query.in(TableReservation::getStatus, TableReservationStatus.COMPLETED,
                    TableReservationStatus.CANCELLED, TableReservationStatus.NO_SHOW);
        }
        if ("active".equals(statusGroup)) query.orderByAsc(TableReservation::getExpectedArrivalTime);
        else query.orderByDesc(TableReservation::getExpectedArrivalTime);
        if (storeId == null && storeDataScopeService.requiresStoreRestriction()) {
            List<Long> allowed = storeDataScopeService.getAllowedStoreIds();
            if (allowed.isEmpty()) return new Page<>(currentPage, pageSize, 0);
            query.in(TableReservation::getStoreId, allowed);
        }
        Page<TableReservation> raw = page(new Page<>(currentPage, Math.min(pageSize, 200)), query);
        Page<TableReservationVO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(toVOList(raw.getRecords()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TableReservationVO createReservation(TableReservationRequest request, Long operatorId) {
        ValidatedReservation validated = validateAndLock(request, null);
        TableReservation reservation = new TableReservation()
                .setStoreId(validated.table().getStoreId())
                .setTableId(validated.table().getId())
                .setReservationPhone(validated.phone())
                .setPeopleCount(request.getPeopleCount())
                .setExpectedArrivalTime(request.getExpectedArrivalTime())
                .setExpectedEndTime(request.getExpectedEndTime())
                .setReservationRemark(validated.remark())
                .setStatus(TableReservationStatus.PENDING)
                .setOperatorId(operatorId)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        save(reservation);
        return toVO(reservation, validated.table(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TableReservationVO updateReservation(TableReservationRequest request) {
        if (request == null || request.getId() == null) throw new BusinessException(400, "预约记录不能为空");
        TableReservation existing = getOne(new LambdaQueryWrapper<TableReservation>()
                .eq(TableReservation::getId, request.getId()).last("FOR UPDATE"));
        if (existing == null) throw new BusinessException(404, "预约记录不存在或无权操作");
        if (existing.getStatus() != TableReservationStatus.PENDING) {
            throw new BusinessException(409, "只有待到店预约可以编辑");
        }
        if (request.getTableId() == null) request.setTableId(existing.getTableId());
        ValidatedReservation validated = validateAndLock(request, existing.getId());
        existing.setStoreId(validated.table().getStoreId())
                .setTableId(validated.table().getId())
                .setReservationPhone(validated.phone())
                .setPeopleCount(request.getPeopleCount())
                .setExpectedArrivalTime(request.getExpectedArrivalTime())
                .setExpectedEndTime(request.getExpectedEndTime())
                .setReservationRemark(validated.remark())
                .setUpdatedAt(LocalDateTime.now());
        updateById(existing);
        return toVO(existing, validated.table(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelReservation(Long id, String reason) {
        TableReservation existing = getReservationForChange(id);
        if (existing.getStatus() != TableReservationStatus.PENDING) {
            throw new BusinessException(409, "只有待到店预约可以取消");
        }
        String normalized = trim(reason);
        if (normalized != null && normalized.length() > 200) throw new BusinessException(400, "取消原因不能超过200个字符");
        return update(new LambdaUpdateWrapper<TableReservation>()
                .eq(TableReservation::getId, id).eq(TableReservation::getStatus, TableReservationStatus.PENDING)
                .set(TableReservation::getStatus, TableReservationStatus.CANCELLED)
                .set(TableReservation::getCancelReason, normalized)
                .set(TableReservation::getCancelledAt, LocalDateTime.now())
                .set(TableReservation::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markNoShow(Long id) {
        TableReservation existing = getReservationForChange(id);
        if (existing.getStatus() != TableReservationStatus.PENDING) {
            throw new BusinessException(409, "只有待到店预约可以标记爽约");
        }
        return update(new LambdaUpdateWrapper<TableReservation>()
                .eq(TableReservation::getId, id).eq(TableReservation::getStatus, TableReservationStatus.PENDING)
                .set(TableReservation::getStatus, TableReservationStatus.NO_SHOW)
                .set(TableReservation::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public void markArrived(Long reservationId, Long tableId, String sessionNo) {
        if (reservationId == null) return;
        TableReservation existing = getOne(new LambdaQueryWrapper<TableReservation>()
                .eq(TableReservation::getId, reservationId).last("FOR UPDATE"));
        if (existing == null || !Objects.equals(existing.getTableId(), tableId)) {
            throw new BusinessException(404, "预约记录与当前桌位不匹配");
        }
        if (existing.getStatus() != TableReservationStatus.PENDING) {
            throw new BusinessException(409, "该预约不是待到店状态");
        }
        update(new LambdaUpdateWrapper<TableReservation>()
                .eq(TableReservation::getId, reservationId).eq(TableReservation::getStatus, TableReservationStatus.PENDING)
                .set(TableReservation::getStatus, TableReservationStatus.ARRIVED)
                .set(TableReservation::getSessionNo, sessionNo)
                .set(TableReservation::getArrivedAt, LocalDateTime.now())
                .set(TableReservation::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public void completeBySessionNo(String sessionNo) {
        if (!StringUtils.hasText(sessionNo)) return;
        completeBySessionNos(List.of(sessionNo));
    }

    @Override
    public void completeBySessionNos(Collection<String> sessionNos) {
        if (sessionNos == null || sessionNos.isEmpty()) return;
        update(new LambdaUpdateWrapper<TableReservation>()
                .in(TableReservation::getSessionNo, sessionNos)
                .eq(TableReservation::getStatus, TableReservationStatus.ARRIVED)
                .set(TableReservation::getStatus, TableReservationStatus.COMPLETED)
                .set(TableReservation::getCompletedAt, LocalDateTime.now())
                .set(TableReservation::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public Map<Long, TableReservation> findNextByTableIds(Collection<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) return Collections.emptyMap();
        List<TableReservation> list = list(new LambdaQueryWrapper<TableReservation>()
                .in(TableReservation::getTableId, tableIds)
                .eq(TableReservation::getStatus, TableReservationStatus.PENDING)
                .ge(TableReservation::getExpectedEndTime, LocalDateTime.now())
                .orderByAsc(TableReservation::getExpectedArrivalTime));
        Map<Long, TableReservation> result = new LinkedHashMap<>();
        for (TableReservation item : list) result.putIfAbsent(item.getTableId(), item);
        return result;
    }

    private ValidatedReservation validateAndLock(TableReservationRequest request, Long excludedId) {
        if (request == null || request.getTableId() == null) throw new BusinessException(400, "桌位不能为空");
        String phone = trim(request.getReservationPhone());
        if (phone == null || !phone.matches("^1\\d{10}$")) throw new BusinessException(400, "请输入正确的11位手机号");
        if (request.getPeopleCount() == null || request.getPeopleCount() < 1 || request.getPeopleCount() > 30) {
            throw new BusinessException(400, "预约人数必须在1-30之间");
        }
        if (request.getExpectedArrivalTime() == null || request.getExpectedEndTime() == null) {
            throw new BusinessException(400, "请选择预计到店和结束时间");
        }
        if (!request.getExpectedEndTime().isAfter(request.getExpectedArrivalTime())) {
            throw new BusinessException(400, "预计结束时间必须晚于到店时间");
        }
        if (request.getExpectedArrivalTime().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new BusinessException(400, "预计到店时间不能早于当前时间");
        }
        String remark = trim(request.getReservationRemark());
        if (remark != null && remark.length() > 200) throw new BusinessException(400, "预约备注不能超过200个字符");

        // 锁定父桌位，使同一桌的并发排期串行执行，避免冲突检查后同时插入。
        TableInfo table = tableInfoMapper.selectOne(new LambdaQueryWrapper<TableInfo>()
                .eq(TableInfo::getId, request.getTableId()).last("FOR UPDATE"));
        if (table == null) throw new BusinessException(404, "桌位不存在或无权操作");
        storeDataScopeService.checkStoreAccess(table.getStoreId());
        if (table.getStatus() == TableStatusEnum.DISABLED) throw new BusinessException(409, "停用桌位不能新增预约");

        LambdaQueryWrapper<TableReservation> conflict = new LambdaQueryWrapper<TableReservation>()
                .eq(TableReservation::getTableId, table.getId())
                .in(TableReservation::getStatus, TableReservationStatus.PENDING, TableReservationStatus.ARRIVED)
                .lt(TableReservation::getExpectedArrivalTime, request.getExpectedEndTime())
                .gt(TableReservation::getExpectedEndTime, request.getExpectedArrivalTime())
                .ne(excludedId != null, TableReservation::getId, excludedId)
                .last("LIMIT 1");
        if (count(conflict) > 0) throw new BusinessException(409, "该桌位所选时段已有预约，请调整时间");
        return new ValidatedReservation(table, phone, remark);
    }

    private TableReservation getReservationForChange(Long id) {
        if (id == null) throw new BusinessException(400, "预约记录不能为空");
        TableReservation existing = getOne(new LambdaQueryWrapper<TableReservation>()
                .eq(TableReservation::getId, id).last("FOR UPDATE"));
        if (existing == null) throw new BusinessException(404, "预约记录不存在或无权操作");
        storeDataScopeService.checkStoreAccess(existing.getStoreId());
        return existing;
    }

    private List<TableReservationVO> toVOList(List<TableReservation> reservations) {
        if (reservations.isEmpty()) return Collections.emptyList();
        Map<Long, TableInfo> tables = tableInfoMapper.selectByIds(reservations.stream()
                .map(TableReservation::getTableId).distinct().toList()).stream()
                .collect(Collectors.toMap(TableInfo::getId, Function.identity()));
        Map<Long, StoreInfo> stores = storeInfoMapper.selectByIds(reservations.stream()
                .map(TableReservation::getStoreId).distinct().toList()).stream()
                .collect(Collectors.toMap(StoreInfo::getId, Function.identity()));
        return reservations.stream().map(item -> toVO(item, tables.get(item.getTableId()), stores.get(item.getStoreId()))).toList();
    }

    private TableReservationVO toVO(TableReservation item, TableInfo table, StoreInfo store) {
        TableReservationVO vo = new TableReservationVO();
        BeanUtils.copyProperties(item, vo);
        if (table != null) {
            vo.setTableNumber(table.getTableNumber());
            vo.setTableName(table.getTableName());
        }
        if (store == null && item.getStoreId() != null) store = storeInfoMapper.selectById(item.getStoreId());
        if (store != null) vo.setStoreName(store.getStoreName());
        vo.setStatusDesc(TableReservationStatus.description(item.getStatus()));
        return vo;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private record ValidatedReservation(TableInfo table, String phone, String remark) {}
}
