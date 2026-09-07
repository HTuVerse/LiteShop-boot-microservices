package org.hongtu.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 服务间传递的精简门店信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreSummaryDTO {
    private Long storeId;
    private String storeName;
}
