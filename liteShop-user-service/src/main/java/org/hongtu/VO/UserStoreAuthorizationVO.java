package org.hongtu.VO;
import lombok.Data;
import java.util.List;
@Data
public class UserStoreAuthorizationVO {
    private Long userId;
    private String username;
    private String nickname;
    private String phone;
    private boolean allStores;
    private List<Long> storeIds = List.of();
    private Long defaultStoreId;
}
