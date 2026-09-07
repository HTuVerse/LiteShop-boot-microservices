package org.hongtu.VO;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 店长可进入的门店工作空间。 */
@Data
@AllArgsConstructor
public class AdminStoreOptionVO {
    private Long storeId;
    private String storeName;
    private boolean defaultStore;
}
