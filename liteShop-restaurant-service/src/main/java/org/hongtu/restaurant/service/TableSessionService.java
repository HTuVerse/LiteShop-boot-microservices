package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.DTO.TableSession;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.restaurant.VO.ScanTableRequest;
import org.hongtu.restaurant.VO.TableSessionVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 餐桌会话表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface TableSessionService extends IService<TableSession> {

    /**
     * 扫码加入餐桌（统一入口）
     * 自动判断是开桌还是加入现有会话
     *
     * @param request 扫码请求
     * @return 会话信息
     */
    TableSessionVO scanAndJoinTable(ScanTableRequest request);

    /**
     * 更新就餐人数
     * @param sessionNo 会话编号
     * @param peopleCount 新的就餐人数
     * @return 更新后的会话信息
     */
    TableSessionVO updatePeopleCount(String sessionNo, Integer peopleCount);

    /**
     * 根据桌位ID获取当前进行中的会话
     * @param tableId 桌位ID
     * @return 会话信息
     */
    TableSession getActiveSessionByTableId(Long tableId);

    /**
     * 根据会话编号获取会话详情（包含订单列表）
     * @param sessionNo 会话编号
     * @return 会话详情
     */
    TableSessionVO getSessionDetailByNo(String sessionNo);

    /**
     * 根据会话ID获取会话详情（包含订单列表）
     * @param sessionId 会话ID
     * @return 会话详情
     */
    TableSessionVO getSessionDetailById(Long sessionId);

    /**
     * 更新会话统计信息（订单数、总金额）
     * @param sessionNo 会话编号
     */
    void updateSessionStatistics(String sessionNo);

    /**
     * 结束会话（结账）
     * @param sessionNo 会话编号
     * @param paidAmount 实付金额
     * @return 是否成功
     */
    boolean endSession(String sessionNo, BigDecimal paidAmount);

    /**
     * 增加在线人数
     * @param sessionNo 会话编号
     */
    void increaseUserCount(String sessionNo);

    /**
     * 减少在线人数
     * @param sessionNo 会话编号
     */
    void decreaseUserCount(String sessionNo);

    /**
     * 验证会话有效性
     * @param sessionNo 会话编号
     * @return 在线人数
     */
    Boolean validateSession(String sessionNo);

    /**
     * 获取所有进行中的会话（用于后台管理）
     * @param storeId 门店ID
     * @return 会话列表
     */
    List<TableSessionVO> getActiveSessionList(Long storeId);

    /**
     * 分页查询会话列表
     * @param storeId 门店ID
     * @param status 状态
     * @param page 分页对象
     * @return 分页会话列表
     */
    Page<TableSessionVO> getSessionPage(Long storeId, Integer status, Page<TableSessionVO> page);

    /**
     * 一键清除所有进行中的会话，恢复餐桌状态
     * @param storeId 门店ID（可选，为null时清除所有门店）
     * @return 清除的会话数量
     */
    int clearAllSessions(Long storeId);

    /**
     * 批量清除指定餐桌的会话，恢复餐桌状态
     * @param tableIds 餐桌ID列表
     * @return 清除的会话数量
     */
    int clearSessionsByTableIds(List<Long> tableIds);

}
