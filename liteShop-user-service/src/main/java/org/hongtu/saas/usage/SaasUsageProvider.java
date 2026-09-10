package org.hongtu.saas.usage;

import java.math.BigDecimal;

/** 实时资源用量 SPI。月订单等累计型指标继续使用 saas_usage_counter。 */
public interface SaasUsageProvider {
    String metric();
    BigDecimal currentUsage(Long tenantId);
}
