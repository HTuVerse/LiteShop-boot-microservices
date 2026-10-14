package org.hongtu.service.impl;

import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DAO.VerifyRecordMapper;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.DTO.VerifyRecord;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.VerifyStatusEnum;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单 A 的延迟返还不能解开已经被订单 B 使用的优惠券。
 */
@ExtendWith(MockitoExtension.class)
class CouponRestoreBindingTest {

    @Mock
    private VerifyRecordMapper verifyRecordMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @InjectMocks
    private UserCouponServiceImpl userCouponService;

    @Test
    void missingOrderRecordDoesNotRestoreCoupon() {
        when(verifyRecordMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> userCouponService.restoreCouponOnCancel(8L, 100L));
        verify(userCouponMapper, never()).update(any(), any());
    }

    @Test
    void alreadyRestoredOrderDoesNotTouchCouponUsedByAnotherOrder() {
        VerifyRecord restored = new VerifyRecord();
        restored.setStatus(VerifyStatusEnum.NOT_VERIFY);
        restored.setOrderId(100L);
        when(verifyRecordMapper.selectOne(any())).thenReturn(restored);
        UserCoupon usedByOtherOrder = new UserCoupon();
        usedByOtherOrder.setStatus(CouponStatusEnum.USED);
        userCouponService.restoreCouponOnCancel(8L, 100L);
        verify(userCouponMapper, never()).selectById(8L);
    }
}
