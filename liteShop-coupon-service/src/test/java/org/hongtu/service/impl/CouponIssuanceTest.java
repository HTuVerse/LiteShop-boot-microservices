package org.hongtu.service.impl;
import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.UserContactDTO;
import org.hongtu.controller.UserCouponController;
import org.hongtu.enums.OnOrOffShelfEnum;
import org.hongtu.service.UserCouponService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class CouponIssuanceTest {
    @Test void controllerPreservesLongIdAndParsesOptionalDate() throws Exception {
        UserCouponService service=mock(UserCouponService.class);var controller=new UserCouponController();
        ReflectionTestUtils.setField(controller,"userCouponService",service);
        when(service.sendCouponToUser(anyLong(),anyLong(),nullable(Date.class),nullable(Date.class))).thenReturn(Result.success());
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/user-coupon/send").param("userId","9223372036854775807").param("couponId","42").param("expiredTime","2099-09-03 12:00:00"))
            .andExpect(status().isOk());
        verify(service).sendCouponToUser(eq(Long.MAX_VALUE),eq(42L),isNull(),any(Date.class));
    }
    @Test void partialPeriodDefaultsWithoutExceedingCouponExpiry() {
        var service=spy(new UserCouponServiceImpl()); var users=mock(UserFeignClient.class); var coupons=mock(CouponMapper.class);var issued=mock(UserCouponMapper.class);
        ReflectionTestUtils.setField(service,"userFeignClient",users);ReflectionTestUtils.setField(service,"couponMapper",coupons);ReflectionTestUtils.setField(service,"userCouponMapper",issued);
        when(users.getUserById(5L)).thenReturn(Result.success(new UserContactDTO()));
        var coupon=new Coupon();coupon.setStatus(OnOrOffShelfEnum.ON_SHELF);coupon.setRemainCount(2);coupon.setEndTime(LocalDateTime.now().plusDays(1));
        when(coupons.selectById(42L)).thenReturn(coupon);when(coupons.decreaseStock(42L,1)).thenReturn(1);when(issued.selectCount(any())).thenReturn(0L);
        doReturn(true).when(service).save(any(UserCoupon.class));
        assertEquals(200,service.sendCouponToUser(5L,42L,null,null).getCode());
        var capture=ArgumentCaptor.forClass(UserCoupon.class);verify(service).save(capture.capture());
        assertEquals(coupon.getEndTime(),capture.getValue().getExpireTime());
        assertNotNull(capture.getValue().getStartTime());
    }
    @Test void missingUserAndInvalidPeriodNeverConsumeStock() {
        var service=new UserCouponServiceImpl();var users=mock(UserFeignClient.class);var coupons=mock(CouponMapper.class);
        ReflectionTestUtils.setField(service,"userFeignClient",users);ReflectionTestUtils.setField(service,"couponMapper",coupons);
        when(users.getUserById(1L)).thenReturn(Result.success((UserContactDTO)null));
        assertNotEquals(200,service.sendCouponToUser(1L,42L,null,null).getCode());verifyNoInteractions(coupons);
        when(users.getUserById(1L)).thenReturn(Result.success(new UserContactDTO()));
        var coupon=new Coupon();coupon.setStatus(OnOrOffShelfEnum.ON_SHELF);coupon.setRemainCount(2);
        when(coupons.selectById(42L)).thenReturn(coupon);
        assertNotEquals(200,service.sendCouponToUser(1L,42L,new Date(System.currentTimeMillis()+100000),new Date()).getCode());
        verify(coupons,never()).decreaseStock(anyLong(),anyInt());
    }
}
