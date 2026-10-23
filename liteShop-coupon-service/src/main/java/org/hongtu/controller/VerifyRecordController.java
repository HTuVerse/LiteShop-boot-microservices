package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.VerifyRecord;
import org.hongtu.Result.Result;
import org.hongtu.VO.VerifyResultVO;
import org.hongtu.enums.VerifyStatusEnum;
import org.hongtu.service.VerifyRecordService;
import org.springframework.web.bind.annotation.*;

/**
 * 核销记录 前端控制器
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@RestController
@RequestMapping("/verify-record")
public class VerifyRecordController {

    @Resource
    VerifyRecordService verifyRecordService;

    /**
     * 核销预览（扫码后先查看优惠券信息）
     * @param couponCode 优惠券唯一码（15位数字）
     * @return 优惠券详情及是否可核销
     */
    @SaCheckPermission(value = {"operations:verify-record", "marketing:coupon", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    @GetMapping("/preview")
    public Result<VerifyResultVO> previewVerify(@RequestParam String couponCode) {
        
            return verifyRecordService.previewVerify(couponCode);
        
    }

    /**
     * 核销优惠券
     * @param couponCode 优惠券唯一码（15位数字，用户展示二维码的内容）
     * @return 核销结果（包含优惠券和用户详情）
     */
    @PostMapping("/verify")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"operations:verify-record:verify", "marketing:coupon:verify", "operations:direct-cashier:verify", "operations:table-cashier:verify"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<VerifyResultVO> verifyCode(@RequestParam String couponCode) {
        
            // 获取当前登录的管理员ID
            Long merchantId = StpUtil.getLoginIdAsLong();
            // 执行核销
            return verifyRecordService.verifyCode(couponCode, merchantId);
        
    }

    /**
     * 分页查询核销记录
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param merchantId 核销人ID（可选）
     * @param status 核销状态：0-未核销，1-已核销
     * @return 分页结果
     */
    @GetMapping("/list")
    @SaCheckPermission(value = {"operations:verify-record", "marketing:coupon", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<VerifyRecord>> getVerifyRecordList(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer status) {
        
            VerifyStatusEnum statusEnum = status != null ? VerifyStatusEnum.getByCode(status) : null;
            // 非营销管理员仅可查看自己的核销记录，不能通过 merchantId 枚举其他操作人。
            Long scopedMerchantId = canViewAllRecords() ? merchantId : Long.valueOf(StpUtil.getLoginIdAsLong());
            return verifyRecordService.getVerifyRecordList(currentPage, pageSize, scopedMerchantId, statusEnum);
        
    }

    /**
     * 获取核销记录详情
     * @param id 记录ID
     * @return 记录详情
     */
    @GetMapping("/detail")
    @SaCheckPermission(value = {"operations:verify-record", "marketing:coupon", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<VerifyRecord> getVerifyRecordById(@RequestParam Long id) {
            Result<VerifyRecord> result = verifyRecordService.getVerifyRecordById(id);
            if (result.getData() != null && !canViewAllRecords()
                    && !Long.valueOf(StpUtil.getLoginIdAsLong()).equals(result.getData().getMerchantId())) {
                return Result.error(403, "无权查看该核销记录");
            }
            return result;
        
    }

    /**
     * 查询当前管理员的核销记录
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @SaCheckPermission(value = {"operations:verify-record", "marketing:coupon", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    @GetMapping("/my-records")
    public Result<Page<VerifyRecord>> getMyVerifyRecords(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
            Long merchantId = StpUtil.getLoginIdAsLong();
            return verifyRecordService.getVerifyRecordList(currentPage, pageSize, merchantId, null);
        
    }

    /** 营销管理员可查看本租户全部核销记录，普通核销员仅可查看自己的记录。 */
    private boolean canViewAllRecords() {
        return StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasPermission("marketing:coupon");
    }

}
