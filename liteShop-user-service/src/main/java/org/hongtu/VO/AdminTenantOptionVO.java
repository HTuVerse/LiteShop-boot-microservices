package org.hongtu.VO;

import lombok.Data;

import java.util.List;

/** 登录预认证通过后，当前运营账号可选择的商户。 */
@Data
public class AdminTenantOptionVO {

    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private List<String> roleCodes;
    private List<AdminStoreOptionVO> stores;
    private boolean storeSelectionRequired;

    public AdminTenantOptionVO(Long tenantId, String tenantCode, String tenantName) {
        this(tenantId, tenantCode, tenantName, List.of(), List.of(), false);
    }

    public AdminTenantOptionVO(Long tenantId, String tenantCode, String tenantName,
                               List<String> roleCodes, List<AdminStoreOptionVO> stores,
                               boolean storeSelectionRequired) {
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        this.stores = stores == null ? List.of() : List.copyOf(stores);
        this.storeSelectionRequired = storeSelectionRequired;
    }
}
