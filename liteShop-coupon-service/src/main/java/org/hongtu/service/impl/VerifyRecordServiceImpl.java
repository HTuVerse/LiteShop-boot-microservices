package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponMapper;
import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DAO.VerifyRecordMapper;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.DTO.Coupon;
import org.hongtu.api.dto.UserContactDTO;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.DTO.VerifyRecord;
import org.hongtu.Result.Result;
import org.hongtu.VO.VerifyResultVO;
import org.hongtu.enums.CodeMsg;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.VerifyStatusEnum;
import org.hongtu.service.VerifyRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * <p>
 * 核销记录 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@Service
public class VerifyRecordServiceImpl extends ServiceImpl<VerifyRecordMapper, VerifyRecord> implements VerifyRecordService {

    @Resource
    UserCouponMapper userCouponMapper;
    @Resource
    CouponMapper couponMapper;
    @Resource
    UserFeignClient userFeignClient;

    /**
     * 核销优惠券（通过优惠券唯一码）
     * @param couponCode 优惠券唯一码（15位数字）
     * @param merchantId 核销人ID
     * @return 核销结果（包含优惠券和用户详情）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<VerifyResultVO> verifyCode(String couponCode, Long merchantId) {
        // 先进行预览校验
        Result<VerifyResultVO> previewResult = previewVerify(couponCode);
        if (!Result.CODE_SUCCESS.equals(previewResult.getCode())) {
            return previewResult;
        }
        VerifyResultVO resultVO = previewResult.getData();
        // 检查是否可核销
        if (!Boolean.TRUE.equals(resultVO.getCanVerify())) {
            return Result.error(CodeMsg.NOT_FIND.getCode(), resultVO.getVerifyMessage());
        }
        // 获取用户优惠券
        LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserCoupon::getCode, couponCode);
        UserCoupon userCoupon = userCouponMapper.selectOne(queryWrapper);
        // 条件更新保证扫码核销不会覆盖同时发生的回收操作。
        if (userCoupon == null || userCouponMapper.casVerifyCoupon(userCoupon.getId(), LocalDateTime.now()) != 1) {
            return Result.error("优惠券状态已变更，请刷新后重试");
        }
        // 创建核销记录
        VerifyRecord verifyRecord = new VerifyRecord();
        verifyRecord.setUserCouponId(userCoupon.getId());
        verifyRecord.setMerchantId(merchantId);
        verifyRecord.setVerifyCode(couponCode);
        verifyRecord.setVerifyTime(LocalDateTime.now());
        verifyRecord.setStatus(VerifyStatusEnum.VERIFYED);
        save(verifyRecord);
        // 更新返回结果
        resultVO.setVerifyRecordId(verifyRecord.getId());
        resultVO.setVerifyStatus(VerifyStatusEnum.VERIFYED);
        resultVO.setCouponStatus(CouponStatusEnum.USED);
        resultVO.setVerifyTime(verifyRecord.getVerifyTime());
        resultVO.setCanVerify(false);
        resultVO.setVerifyMessage("核销成功");
        log.info("核销成功：couponCode={}, merchantId={}, userCouponId={}", couponCode, merchantId, userCoupon.getId());
        return Result.success("核销成功", resultVO);
    }

    /**
     * 核销预览（扫码后查看优惠券信息，确认后再核销）
     * @param couponCode 优惠券唯一码
     * @return 优惠券详情及是否可核销
     */
    @Override
    public Result<VerifyResultVO> previewVerify(String couponCode) {
        // 参数校验
        if (!StringUtils.hasText(couponCode)) {
            return Result.error(CodeMsg.PARAM_ERROR.getCode(), "优惠券码不能为空");
        }
        // 根据优惠券码查询用户优惠券
        LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserCoupon::getCode, couponCode);
        UserCoupon userCoupon = userCouponMapper.selectOne(queryWrapper);
        if (userCoupon == null) {
            log.warn("核销预览失败：优惠券码不存在，couponCode={}", couponCode);
            return Result.error(CodeMsg.NOT_FIND.getCode(), "优惠券码无效");
        }
        // 查询优惠券详情
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        // 查询用户信息（微服务改造：经 Feign 调 user-service）
        UserContactDTO user = FeignResultUtil.unwrap(userFeignClient.getUserById(userCoupon.getUserId()));
        // 构建返回结果
        VerifyResultVO resultVO = new VerifyResultVO();
        resultVO.setUserCouponId(userCoupon.getId());
        resultVO.setCouponCode(couponCode);
        resultVO.setCouponId(userCoupon.getCouponId());
        resultVO.setStartTime(userCoupon.getStartTime());
        resultVO.setExpireTime(userCoupon.getExpireTime());
        resultVO.setUserId(userCoupon.getUserId());
        // 设置优惠券信息
        if (coupon != null) {
            resultVO.setCouponName(coupon.getName());
            resultVO.setCouponDescription(coupon.getDescription());
            resultVO.setCouponType(coupon.getType());
            resultVO.setCouponValue(coupon.getValue());
            resultVO.setMinAmount(coupon.getMinAmount());
        }
        // 设置用户信息（手机号脱敏）
        if (user != null) {
            String phone = user.getPhone();
            if (StringUtils.hasText(phone) && phone.length() >= 11) {
                resultVO.setUserName(phone.substring(0, 3) + "****" + phone.substring(7));
            } else {
                resultVO.setUserName(user.getUsername());
            }
        }
        // 检查核销状态
        resultVO.setVerifyStatus(VerifyStatusEnum.NOT_VERIFY);
        resultVO.setCouponStatus(userCoupon.getStatus());
        // 已核销时展示核销时间（来自 user_coupon.useTime）
        if (userCoupon.getStatus() == CouponStatusEnum.USED && userCoupon.getUseTime() != null) {
            resultVO.setVerifyStatus(VerifyStatusEnum.VERIFYED);
            resultVO.setVerifyTime(userCoupon.getUseTime());
        }
        // 校验是否可核销
        String verifyMessage = checkCanVerify(userCoupon);
        if (verifyMessage == null) {
            resultVO.setCanVerify(true);
            resultVO.setVerifyMessage("可以核销");
        } else {
            resultVO.setCanVerify(false);
            resultVO.setVerifyMessage(verifyMessage);
        }
        return Result.success(resultVO);
    }

    /**
     * 检查优惠券是否可核销
     * @param userCoupon 用户优惠券
     * @return null表示可核销，否则返回不可核销原因
     */
    private String checkCanVerify(UserCoupon userCoupon) {
        Coupon template = couponMapper.selectById(userCoupon.getCouponId());
        if (template == null) return "优惠券模板不存在";
        if (org.hongtu.utils.CouponUnavailableDay.matches(template.getUnavailableDates(),
                template.getUnavailableHolidays(), LocalDateTime.now().toLocalDate())) return "优惠券今天不可使用";
        // 检查是否已核销过
        LambdaQueryWrapper<VerifyRecord> verifyQuery = new LambdaQueryWrapper<>();
        verifyQuery.eq(VerifyRecord::getUserCouponId, userCoupon.getId())
                .eq(VerifyRecord::getStatus, VerifyStatusEnum.VERIFYED);
        Long verifyCount = count(verifyQuery);
        if (verifyCount > 0) {
            return "优惠券已被核销，请勿重复操作";
        }
        // 检查优惠券状态
        if (userCoupon.getStatus() == CouponStatusEnum.USED) {
            return "优惠券已使用";
        }
        if (userCoupon.getStatus() == CouponStatusEnum.OUT_OF_DATE) {
            return "优惠券已过期";
        }
        if (userCoupon.getStatus() == CouponStatusEnum.NOT_EFFECTIVE) {
            return "优惠券还未生效";
        }
        if (userCoupon.getStatus() == CouponStatusEnum.REVOKED) {
            return "优惠券已回收";
        }
        if (userCoupon.getStatus() != CouponStatusEnum.NOT_USE) {
            return "优惠券状态不可核销";
        }
        // 实时检查是否过期
        if (userCoupon.getExpireTime() != null && userCoupon.getExpireTime().isBefore(LocalDateTime.now())) {
            // 更新状态
            userCouponMapper.casExpireCoupon(userCoupon.getId());
            return "优惠券已过期";
        }
        // 实时检查是否生效
        if (userCoupon.getStartTime() != null && userCoupon.getStartTime().isAfter(LocalDateTime.now())) {
            return "优惠券还未生效";
        }
        return null;
    }

    /**
     * 分页查询核销记录
     */
    @Override
    public Result<Page<VerifyRecord>> getVerifyRecordList(Integer currentPage, Integer pageSize, Long merchantId, VerifyStatusEnum status) {
        Page<VerifyRecord> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<VerifyRecord> queryWrapper = new LambdaQueryWrapper<>();
        // 按核销人筛选
        if (merchantId != null) {
            queryWrapper.eq(VerifyRecord::getMerchantId, merchantId);
        }
        // 按状态筛选
        if (status != null) {
            queryWrapper.eq(VerifyRecord::getStatus, status);
        }
        // 按核销时间倒序
        queryWrapper.orderByDesc(VerifyRecord::getVerifyTime);
        Page<VerifyRecord> result = page(page, queryWrapper);
        return Result.success(result);
    }

    /**
     * 获取核销记录详情
     */
    @Override
    public Result<VerifyRecord> getVerifyRecordById(Long id) {
        if (id == null) {
            return Result.error("记录ID不能为空");
        }
        VerifyRecord record = getById(id);
        if (record == null) {
            return Result.error("核销记录不存在");
        }
        return Result.success(record);
    }
}
