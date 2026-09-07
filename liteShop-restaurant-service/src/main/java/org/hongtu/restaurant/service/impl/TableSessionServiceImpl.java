package org.hongtu.restaurant.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.RestaurantOrderMapper;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DAO.TableInfoMapper;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.TableInfo;
import org.hongtu.restaurant.DTO.TableSession;
import org.hongtu.restaurant.DAO.TableSessionMapper;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.restaurant.VO.ScanTableRequest;
import org.hongtu.restaurant.VO.TableSessionVO;
import org.hongtu.restaurant.convert.RestaurantOrderConverter;
import org.hongtu.restaurant.enums.SessionStatusEnum;
import org.hongtu.restaurant.enums.TableStatusEnum;
import org.hongtu.restaurant.netty.NettyWebSocketPushService;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.restaurant.service.TableReservationService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static org.hongtu.enums.CodeMsg.DINNER_NOT_EXIST;
import static org.hongtu.enums.CodeMsg.DINNER_STATUS_ERROR;

/**
 * <p>
 * 餐桌会话表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
@Slf4j
public class TableSessionServiceImpl extends ServiceImpl<TableSessionMapper, TableSession> implements TableSessionService {

    @Resource
    TableInfoMapper tableInfoMapper;
    @Resource
    StoreInfoMapper storeInfoMapper;
    @Resource
    RestaurantOrderMapper restaurantOrderMapper;
    @Resource
    RestaurantOrderConverter restaurantOrderConverter;
    @Resource
    NettyWebSocketPushService webSocketPushService;
    @Resource
    TableReservationService tableReservationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TableSessionVO scanAndJoinTable(ScanTableRequest request) {
        // 1. 检查桌位是否存在
        TableInfo tableInfo = tableInfoMapper.selectOne(new LambdaQueryWrapper<TableInfo>()
                .eq(TableInfo::getId, request.getTableId()).last("FOR UPDATE"));
        if (tableInfo == null) {
            throw new RuntimeException("桌位不存在");
        }
        if (tableInfo.getStatus() == TableStatusEnum.DISABLED) {
            throw new org.hongtu.exception.BusinessException(409, "桌位已停用，恢复空闲后才能开桌");
        }
        // 2. 查询该桌位是否有进行中的会话
        TableSession existSession = getActiveSessionByTableId(request.getTableId());

        if (existSession != null) {
            // 情况1：已有进行中的会话 → 加入会话
            log.info("加入现有会话: tableId={}, sessionNo={}, userName={}",
                    request.getTableId(), existSession.getSessionNo(), request.getUserName());
            tableReservationService.markArrived(request.getReservationId(), request.getTableId(), existSession.getSessionNo());
            return getSessionDetailById(existSession.getId());
        } else {
            // 情况2：没有进行中的会话 → 创建新会话（开桌）
            log.info("创建新会话（开桌）: tableId={}, peopleCount={}, userName={}",
                    request.getTableId(), request.getPeopleCount(), request.getUserName());

            // 创建新会话
            TableSession session = new TableSession();
            session.setSessionNo(generateSessionNo());
            session.setStoreId(tableInfo.getStoreId());
            session.setTableId(request.getTableId());
            session.setPeopleCount(request.getPeopleCount() != null ? request.getPeopleCount() : 1);
            session.setCurrentUserCount(1);
            session.setStartTime(LocalDateTime.now());
            session.setTotalOrders(0);
            session.setTotalAmount(BigDecimal.ZERO);
            session.setPaidAmount(BigDecimal.ZERO);
            session.setStatus(SessionStatusEnum.IN_PROGRESS); // 进行中
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            session.setRemark(request.getRemark());

            this.save(session);

            // 更新桌位状态为"使用中"
            LambdaUpdateWrapper<TableInfo> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(TableInfo::getId, request.getTableId())
                    .set(TableInfo::getStatus, TableStatusEnum.IN_USE.getCode()) // 使用中
                    .set(TableInfo::getUpdatedAt, LocalDateTime.now());
            tableInfoMapper.update(updateWrapper);

            tableReservationService.markArrived(request.getReservationId(), request.getTableId(), session.getSessionNo());

            return getSessionDetailById(session.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TableSessionVO updatePeopleCount(String sessionNo, Integer peopleCount) {
        // 1. 查询会话是否存在
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TableSession::getSessionNo, sessionNo);
        TableSession session = this.getOne(queryWrapper);

        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        if (session.getStatus() != SessionStatusEnum.IN_PROGRESS) {
            throw new RuntimeException("会话已结束，无法修改就餐人数");
        }

        // 2. 更新就餐人数
        LambdaUpdateWrapper<TableSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TableSession::getSessionNo, sessionNo)
                .set(TableSession::getPeopleCount, peopleCount)
                .set(TableSession::getUpdatedAt, LocalDateTime.now());

        boolean updated = this.update(updateWrapper);

        if (!updated) {
            throw new RuntimeException("更新就餐人数失败");
        }

        log.info("更新就餐人数: sessionNo={}, oldCount={}, newCount={}",
                sessionNo, session.getPeopleCount(), peopleCount);

        // 3. 返回更新后的会话信息
        TableSessionVO updatedSession = getSessionDetailByNo(sessionNo);

        // 4. 通过 WebSocket 推送更新通知（可选）
        try {
            webSocketPushService.pushSessionUpdated(sessionNo, updatedSession);
        } catch (Exception e) {
            log.warn("推送会话更新通知失败: {}", e.getMessage());
        }

        return updatedSession;
    }


    @Override
    public TableSession getActiveSessionByTableId(Long tableId) {
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TableSession::getTableId, tableId)
                .eq(TableSession::getStatus, SessionStatusEnum.IN_PROGRESS) // 进行中
                .orderByDesc(TableSession::getCreatedAt)
                .last("LIMIT 1");
        return this.getOne(queryWrapper);
    }

    @Override
    public TableSessionVO getSessionDetailByNo(String sessionNo) {
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TableSession::getSessionNo, sessionNo);
        TableSession session = this.getOne(queryWrapper);
        if (session == null) {
            return null;
        }
        return buildSessionVO(session);
    }

    @Override
    public TableSessionVO getSessionDetailById(Long sessionId) {
        TableSession session = this.getById(sessionId);
        if (session == null) {
            return null;
        }
        return buildSessionVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatistics(String sessionNo) {
        // 查询该会话下的所有订单
        LambdaQueryWrapper<RestaurantOrder> orderQueryWrapper = new LambdaQueryWrapper<>();
        TableSession session = this.lambdaQuery()
                .eq(TableSession::getSessionNo, sessionNo)
                .one();

        if (session == null) {
            return;
        }
        // 查询该桌位订单
        orderQueryWrapper.eq(RestaurantOrder::getSessionNo, sessionNo);
        List<RestaurantOrder> orders = restaurantOrderMapper.selectList(orderQueryWrapper);

        // 统计订单数和总金额
        int totalOrders = orders.size();
        BigDecimal totalAmount = orders.stream()
                .map(RestaurantOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 更新会话统计信息
        LambdaUpdateWrapper<TableSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TableSession::getSessionNo, sessionNo)
                .set(TableSession::getTotalOrders, totalOrders)
                .set(TableSession::getTotalAmount, totalAmount)
                .set(TableSession::getUpdatedAt, LocalDateTime.now());
        this.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean endSession(String sessionNo, BigDecimal paidAmount) {
        // 1. 查询会话
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TableSession::getSessionNo, sessionNo);
        TableSession session = this.getOne(queryWrapper);

        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        if (session.getStatus() == SessionStatusEnum.ENDED) {
            throw new RuntimeException("会话已结束");
        }

        // 2. 更新会话状态
        LambdaUpdateWrapper<TableSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TableSession::getSessionNo, sessionNo)
                .set(TableSession::getStatus, 2) // 已结束
                .set(TableSession::getEndTime, LocalDateTime.now())
                .set(TableSession::getPaidAmount, paidAmount)
                .set(TableSession::getUpdatedAt, LocalDateTime.now());
        this.update(updateWrapper);

        // 3. 更新桌位状态为"空闲"
        LambdaUpdateWrapper<TableInfo> tableUpdateWrapper = new LambdaUpdateWrapper<>();
        tableUpdateWrapper.eq(TableInfo::getId, session.getTableId())
                .set(TableInfo::getStatus, 0) // 空闲
                .set(TableInfo::getUpdatedAt, LocalDateTime.now());
        tableInfoMapper.update(null, tableUpdateWrapper);

        tableReservationService.completeBySessionNo(sessionNo);

        return true;
    }

    @Override
    public void increaseUserCount(String sessionNo) {
        LambdaUpdateWrapper<TableSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TableSession::getSessionNo, sessionNo)
                .setSql("current_user_count = current_user_count + 1")
                .set(TableSession::getUpdatedAt, LocalDateTime.now());
        this.update(updateWrapper);
    }

    @Override
    public void decreaseUserCount(String sessionNo) {
        LambdaUpdateWrapper<TableSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TableSession::getSessionNo, sessionNo)
                .setSql("current_user_count = GREATEST(current_user_count - 1, 0)")
                .set(TableSession::getUpdatedAt, LocalDateTime.now());
        this.update(updateWrapper);
    }

    @Override
    public List<TableSessionVO> getActiveSessionList(Long storeId) {
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(storeId != null, TableSession::getStoreId, storeId)
                .eq(TableSession::getStatus, 1) // 进行中
                .orderByDesc(TableSession::getStartTime);

        List<TableSession> sessions = this.list(queryWrapper);
        return sessions.stream()
                .map(this::buildSessionVO)
                .collect(Collectors.toList());
    }

    @Override
    public Boolean validateSession(String sessionNo){
        TableSession tableSession = this.getOne(new LambdaQueryWrapper<TableSession>()
                .eq(TableSession::getSessionNo, sessionNo));
        if (tableSession == null) {
            throw new BusinessException(DINNER_NOT_EXIST.getCode(),DINNER_NOT_EXIST.getMsg());
        }
        if (tableSession.getStatus() == SessionStatusEnum.ENDED) {
            throw new BusinessException(DINNER_STATUS_ERROR.getCode(),DINNER_STATUS_ERROR.getMsg());
        }
        return true;
    }


    @Override
    public Page<TableSessionVO> getSessionPage(Long storeId, Integer status, Page<TableSessionVO> page) {
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(storeId != null, TableSession::getStoreId, storeId)
                .eq(status != null, TableSession::getStatus, status)
                .orderByDesc(TableSession::getCreatedAt);

        Page<TableSession> sessionPage = new Page<>(page.getCurrent(), page.getSize());
        sessionPage = this.page(sessionPage, queryWrapper);

        List<TableSessionVO> voList = sessionPage.getRecords().stream()
                .map(this::buildSessionVO)
                .collect(Collectors.toList());

        Page<TableSessionVO> resultPage = new Page<>(page.getCurrent(), page.getSize(), sessionPage.getTotal());
        resultPage.setRecords(voList);

        return resultPage;
    }

    /**
     * 构建会话 VO
     */
    private TableSessionVO buildSessionVO(TableSession session) {
        TableSessionVO vo = new TableSessionVO();
        BeanUtils.copyProperties(session, vo);

        // 设置状态描述
        vo.setStatusDesc(session.getStatus() == SessionStatusEnum.IN_PROGRESS ? "进行中" : "已结束");

        // 计算用餐时长
        if (session.getStartTime() != null) {
            LocalDateTime endTime = session.getEndTime() != null ? session.getEndTime() : LocalDateTime.now();
            Duration duration = Duration.between(session.getStartTime(), endTime);
            vo.setDurationMinutes(duration.toMinutes());
        }

        // 查询桌位信息
        if (session.getTableId() != null) {
            TableInfo tableInfo = tableInfoMapper.selectById(session.getTableId());
            if (tableInfo != null) {
                vo.setTableNumber(tableInfo.getTableNumber());
                vo.setTableName(tableInfo.getTableName());
                vo.setTableId(tableInfo.getId());
                // 查询门店信息
                if (tableInfo.getStoreId() != null) {
                    vo.setStoreId(tableInfo.getStoreId());
                    StoreInfo storeInfo = storeInfoMapper.selectById(tableInfo.getStoreId());
                    if (storeInfo != null) {
                        vo.setStoreName(storeInfo.getStoreName());
                    }
                }
            }
        }

        // 查询该会话下的订单
        LambdaQueryWrapper<RestaurantOrder> orderQueryWrapper = new LambdaQueryWrapper<>();
        orderQueryWrapper.eq(RestaurantOrder::getSessionNo, session.getSessionNo());

        orderQueryWrapper.orderByDesc(RestaurantOrder::getCreatedAt);

        List<RestaurantOrder> orders = restaurantOrderMapper.selectList(orderQueryWrapper);

        // 转换为VO
        List<RestaurantOrderVO> orderVOList = restaurantOrderConverter.toVOList(orders);

        vo.setOrders(orderVOList);

        return vo;
    }

    /**
     * 生成会话编号
     */
    private String generateSessionNo() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timeStr = LocalDateTime.now().format(formatter);
        String randomNumbers = RandomUtil.randomNumbers(5);
        return "TBS" + timeStr + randomNumbers;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearAllSessions(Long storeId) {
        // 1. 查询所有进行中的会话
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TableSession::getStatus, SessionStatusEnum.IN_PROGRESS);
        if (storeId != null) {
            queryWrapper.eq(TableSession::getStoreId, storeId);
        }

        List<TableSession> activeSessions = this.list(queryWrapper);

        if (activeSessions.isEmpty()) {
            log.info("没有需要清除的会话, storeId={}", storeId);
            return 0;
        }

        // 2. 收集所有涉及的餐桌ID
        List<Long> tableIds = activeSessions.stream()
                .map(TableSession::getTableId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 批量结束所有进行中的会话
        LambdaUpdateWrapper<TableSession> sessionUpdateWrapper = new LambdaUpdateWrapper<>();
        sessionUpdateWrapper.eq(TableSession::getStatus, SessionStatusEnum.IN_PROGRESS);
        if (storeId != null) {
            sessionUpdateWrapper.eq(TableSession::getStoreId, storeId);
        }
        sessionUpdateWrapper.set(TableSession::getStatus, SessionStatusEnum.ENDED)
                .set(TableSession::getEndTime, LocalDateTime.now())
                .set(TableSession::getUpdatedAt, LocalDateTime.now());
        this.update(sessionUpdateWrapper);

        tableReservationService.completeBySessionNos(activeSessions.stream()
                .map(TableSession::getSessionNo).toList());

        // 4. 批量恢复餐桌状态为空闲
        if (!tableIds.isEmpty()) {
            LambdaUpdateWrapper<TableInfo> tableUpdateWrapper = new LambdaUpdateWrapper<>();
            tableUpdateWrapper.in(TableInfo::getId, tableIds)
                    .eq(TableInfo::getStatus, TableStatusEnum.IN_USE)
                    .set(TableInfo::getStatus, TableStatusEnum.IDLE)
                    .set(TableInfo::getUpdatedAt, LocalDateTime.now());
            tableInfoMapper.update(null, tableUpdateWrapper);
        }

        log.info("一键清除会话完成: storeId={}, 清除会话数={}, 恢复餐桌数={}",
                storeId, activeSessions.size(), tableIds.size());

        return activeSessions.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearSessionsByTableIds(List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return 0;
        }

        // 1. 查询这些餐桌的进行中会话
        LambdaQueryWrapper<TableSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(TableSession::getTableId, tableIds)
                .eq(TableSession::getStatus, SessionStatusEnum.IN_PROGRESS);

        List<TableSession> activeSessions = this.list(queryWrapper);

        if (activeSessions.isEmpty()) {
            log.info("指定餐桌没有需要清除的会话, tableIds={}", tableIds);
            return 0;
        }

        // 2. 批量结束这些会话
        LambdaUpdateWrapper<TableSession> sessionUpdateWrapper = new LambdaUpdateWrapper<>();
        sessionUpdateWrapper.in(TableSession::getTableId, tableIds)
                .eq(TableSession::getStatus, SessionStatusEnum.IN_PROGRESS)
                .set(TableSession::getStatus, SessionStatusEnum.ENDED)
                .set(TableSession::getEndTime, LocalDateTime.now())
                .set(TableSession::getUpdatedAt, LocalDateTime.now());
        this.update(sessionUpdateWrapper);

        tableReservationService.completeBySessionNos(activeSessions.stream()
                .map(TableSession::getSessionNo).toList());

        // 3. 恢复这些餐桌状态为空闲
        LambdaUpdateWrapper<TableInfo> tableUpdateWrapper = new LambdaUpdateWrapper<>();
        tableUpdateWrapper.in(TableInfo::getId, tableIds)
                .eq(TableInfo::getStatus, TableStatusEnum.IN_USE)
                .set(TableInfo::getStatus, TableStatusEnum.IDLE)
                .set(TableInfo::getUpdatedAt, LocalDateTime.now());
        tableInfoMapper.update(null, tableUpdateWrapper);

        log.info("批量清除会话完成: tableIds={}, 清除会话数={}", tableIds, activeSessions.size());

        return activeSessions.size();
    }

}
