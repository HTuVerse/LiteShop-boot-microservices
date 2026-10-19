package org.hongtu.restaurant.service.impl;

import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.Result.Result;
import org.hongtu.api.client.CouponFeignClient;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.restaurant.VO.CouponDiscountResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 优惠券在订单实购菜品和整单门槛下的金额校验。 */
class CouponPricingTest {
    private final CouponFeignClient client = mock(CouponFeignClient.class);
    private final RestaurantOrderServiceImpl service = new RestaurantOrderServiceImpl();

    private CouponDiscountResultVO quote(Coupon coupon, BigDecimal amount,
                                         List<RestaurantOrderServiceImpl.CouponOrderLine> lines) {
        ReflectionTestUtils.setField(service, "couponFeignClient", client);
        UserCoupon issued = new UserCoupon().setId(101L).setUserId(7L)
                .setCouponId(coupon.getId()).setStatus(CouponStatusEnum.NOT_USE);
        when(client.getUserCouponsByIds(List.of(101L))).thenReturn(Result.success(List.of(issued)));
        when(client.getCouponsByIds(List.of(coupon.getId()))).thenReturn(Result.success(List.of(coupon)));
        return (CouponDiscountResultVO) service.calcCouponDiscountWithLines(7L, List.of(101L),
                amount, 1L, lines).getData();
    }

    @Test
    void freeDishPicksOneHighestPricedPurchasedUnit() {
        Coupon coupon = new Coupon().setId(11L).setName("免单券").setType(CouponTypeEnum.DISH_FREE);
        when(client.getItemsByCouponId(11L)).thenReturn(Result.success(List.of(
                new CouponDishItem().setDishId(1L), new CouponDishItem().setDishId(2L))));
        CouponDiscountResultVO result = quote(coupon, new BigDecimal("60"), List.of(
                new RestaurantOrderServiceImpl.CouponOrderLine("0", 1L, "菜品一", 2, new BigDecimal("36")),
                new RestaurantOrderServiceImpl.CouponOrderLine("1", 2L, "菜品二", 1, new BigDecimal("25"))));
        assertTrue(result.getUsable());
        assertEquals(0, result.getTotalDiscountAmount().compareTo(new BigDecimal("25")));
        assertEquals("1", result.getCouponDetails().get(0).getDishItems().get(0).getLineKey());
        assertEquals(1, result.getCouponDetails().get(0).getDishItems().size());
    }

    @Test
    void freeDishWithoutPurchasedMatchIsUnusable() {
        Coupon coupon = new Coupon().setId(11L).setName("免单券").setType(CouponTypeEnum.DISH_FREE);
        when(client.getItemsByCouponId(11L)).thenReturn(Result.success(List.of(
                new CouponDishItem().setDishId(9L))));
        CouponDiscountResultVO result = quote(coupon, new BigDecimal("30"), List.of(
                new RestaurantOrderServiceImpl.CouponOrderLine("0", 1L, "其他菜品", 1, new BigDecimal("30"))));
        assertFalse(result.getUsable());
    }

    @Test
    void twoFreeCouponsCannotConsumeTheSamePurchasedUnit() {
        ReflectionTestUtils.setField(service, "couponFeignClient", client);
        Coupon first = new Coupon().setId(11L).setName("第一张").setType(CouponTypeEnum.DISH_FREE);
        Coupon second = new Coupon().setId(12L).setName("第二张").setType(CouponTypeEnum.DISH_FREE);
        List<Long> issuedIds = List.of(101L, 102L);
        when(client.validateCouponStackRules(issuedIds)).thenReturn(Result.success());
        when(client.getUserCouponsByIds(issuedIds)).thenReturn(Result.success(List.of(
                new UserCoupon().setId(101L).setUserId(7L).setCouponId(11L).setStatus(CouponStatusEnum.NOT_USE),
                new UserCoupon().setId(102L).setUserId(7L).setCouponId(12L).setStatus(CouponStatusEnum.NOT_USE))));
        when(client.getCouponsByIds(List.of(11L, 12L))).thenReturn(Result.success(List.of(first, second)));
        when(client.getItemsByCouponId(anyLong())).thenReturn(Result.success(List.of(
                new CouponDishItem().setDishId(1L))));
        CouponDiscountResultVO result = (CouponDiscountResultVO) service.calcCouponDiscountWithLines(
                7L, issuedIds, new BigDecimal("20"), 1L, List.of(
                        new RestaurantOrderServiceImpl.CouponOrderLine("0", 1L, "菜品", 1, new BigDecimal("20"))))
                .getData();
        assertFalse(result.getUsable());
    }

    @Test
    void discountCouponMustMeetWholeOrderThreshold() {
        Coupon coupon = new Coupon().setId(12L).setName("折扣券")
                .setType(CouponTypeEnum.ORDER_DISCOUNT).setValue(new BigDecimal("0.80"))
                .setMinAmount(new BigDecimal("100"));
        CouponDiscountResultVO result = quote(coupon, new BigDecimal("99"), List.of());
        assertFalse(result.getUsable());
    }

    @Test
    void reductionNeverExceedsPayableAmount() {
        Coupon coupon = new Coupon().setId(13L).setName("满减券")
                .setType(CouponTypeEnum.FULL_REDUCTION).setValue(new BigDecimal("50"));
        CouponDiscountResultVO result = quote(coupon, new BigDecimal("30"), List.of());
        assertTrue(result.getUsable());
        assertEquals(0, result.getTotalDiscountAmount().compareTo(new BigDecimal("30")));
    }
}
