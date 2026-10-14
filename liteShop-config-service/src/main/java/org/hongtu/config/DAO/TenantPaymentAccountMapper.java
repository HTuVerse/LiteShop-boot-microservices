package org.hongtu.config.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.config.DTO.TenantPaymentAccount;

/**
 * 租户支付账户数据访问。查询受当前租户过滤。
 */
@Mapper
public interface TenantPaymentAccountMapper extends BaseMapper<TenantPaymentAccount> {
}
