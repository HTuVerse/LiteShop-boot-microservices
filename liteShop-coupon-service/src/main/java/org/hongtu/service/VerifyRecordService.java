package org.hongtu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.VerifyRecord;
import org.hongtu.Result.Result;
import org.hongtu.VO.VerifyResultVO;
import org.hongtu.enums.VerifyStatusEnum;


/**
 * <p>
 * 核销记录 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
public interface VerifyRecordService extends IService<VerifyRecord> {

    /**
     * 核销优惠券
     * @param couponCode 优惠券唯一码（15位数字）
     * @param merchantId 核销人ID
     * @return 核销结果（包含优惠券和用户详情）
     */
    Result<VerifyResultVO> verifyCode(String couponCode, Long merchantId);

    /**
     * 核销预览（扫码后查看优惠券信息，确认后再核销）
     * @param couponCode 优惠券唯一码
     * @return 优惠券详情及是否可核销
     */
    Result<VerifyResultVO> previewVerify(String couponCode);

    /**
     * 分页查询核销记录
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param merchantId 核销人ID（可选）
     * @param status 核销状态（可选）
     * @return 分页结果
     */
    Result<Page<VerifyRecord>> getVerifyRecordList(Integer currentPage, Integer pageSize, Long merchantId, VerifyStatusEnum status);

    /**
     * 获取核销记录详情
     * @param id 记录ID
     * @return 记录详情
     */
    Result<VerifyRecord> getVerifyRecordById(Long id);
}
