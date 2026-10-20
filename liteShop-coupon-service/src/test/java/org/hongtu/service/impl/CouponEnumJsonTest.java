package org.hongtu.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.enums.OnOrOffShelfEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CouponEnumJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void feignCouponPayloadReadsSerializedDescriptions() throws Exception {
        UserCoupon userCoupon = mapper.readValue("{\"status\":\"未使用\"}", UserCoupon.class);
        assertEquals(CouponStatusEnum.NOT_USE, userCoupon.getStatus());

        Coupon coupon = mapper.readValue(
                "{\"type\":\"菜品免单券\",\"status\":\"上架\",\"stackable\":\"是\"}", Coupon.class);
        assertEquals(CouponTypeEnum.DISH_FREE, coupon.getType());
        assertEquals(OnOrOffShelfEnum.ON_SHELF, coupon.getStatus());
        assertEquals(BooleanEnum.YES, coupon.getStackable());
    }

    @Test
    void numericCodesRemainAcceptedAndUnknownValuesFail() throws Exception {
        assertEquals(CouponStatusEnum.NOT_USE,
                mapper.readValue("{\"status\":1}", UserCoupon.class).getStatus());
        assertEquals(CouponTypeEnum.DISH_FREE,
                mapper.readValue("{\"type\":2}", Coupon.class).getType());
        assertThrows(Exception.class,
                () -> mapper.readValue("{\"status\":\"伪造状态\"}", UserCoupon.class));
    }
}
