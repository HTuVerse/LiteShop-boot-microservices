package org.hongtu.restaurant.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.hongtu.enums.CouponTypeEnum;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus 类型处理器
 * 将数据库中逗号分隔的优惠券类型码（如 "0,2"）与 List<CouponTypeEnum> 互相转换
 *
 * @author Ht
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class CouponTypeListTypeHandler extends BaseTypeHandler<List<CouponTypeEnum>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<CouponTypeEnum> parameter, JdbcType jdbcType) throws SQLException {
        // 空列表存为 null，避免存空字符串
        if (parameter == null || parameter.isEmpty()) {
            ps.setNull(i, java.sql.Types.VARCHAR);
            return;
        }
        String value = parameter.stream()
                .map(e -> String.valueOf(e.getCode()))
                .collect(Collectors.joining(","));
        ps.setString(i, value);
    }

    @Override
    public List<CouponTypeEnum> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseString(rs.getString(columnName));
    }

    @Override
    public List<CouponTypeEnum> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseString(rs.getString(columnIndex));
    }

    @Override
    public List<CouponTypeEnum> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseString(cs.getString(columnIndex));
    }

    /**
     * 解析数据库字符串为枚举列表
     * 当数据库值为 null 或空字符串时返回 null（而不是空列表），
     * 便于业务逻辑通过 couponTypes != null 判断是否使用了优惠券
     */
    private List<CouponTypeEnum> parseString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        List<CouponTypeEnum> result = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return CouponTypeEnum.getByCode(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());
        // 如果解析后为空列表，也返回 null
        return result.isEmpty() ? null : result;
    }
}
