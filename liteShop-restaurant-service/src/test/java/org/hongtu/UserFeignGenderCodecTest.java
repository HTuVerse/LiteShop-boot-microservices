package org.hongtu;

import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.enums.GenderEnum;
import org.hongtu.enums.StatusEnum;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证用户服务响应中的枚举值可以由 Feign 接收端还原。 */
class UserFeignGenderCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void decodesUserResponseWithChineseEnumValues() {
        String response = """
                {"code":200,"data":{"id":1,"gender":"男","status":"正常"}}
                """;

        Result<SysUser> result = mapper.readValue(response,
                mapper.getTypeFactory().constructParametricType(Result.class, SysUser.class));

        assertEquals(GenderEnum.MALE, result.getData().getGender());
        assertEquals(StatusEnum.NORMAL, result.getData().getStatus());
    }

    @Test
    void retainsNumericInputAndChineseOutput() {
        SysUser user = mapper.readValue("{\"gender\":1,\"status\":1}", SysUser.class);

        assertEquals(GenderEnum.MALE, user.getGender());
        assertEquals(StatusEnum.NORMAL, user.getStatus());
        String json = mapper.writeValueAsString(user);
        assertTrue(json.contains("\"gender\":\"男\""));
        assertTrue(json.contains("\"status\":\"正常\""));
    }
}
