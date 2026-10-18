package org.hongtu.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.hongtu.enums.GenderEnum;
import org.hongtu.enums.StatusEnum;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 功能：
 * 作者：
 * 时间：2025/5/7 上午8:35
 */
@Data
public class SysUserVO {

    private Long id;
    private Long tenantId;
    private String tenantCode;
    /** 当前操作商户，与平台账号自身 tenantId 分开。 */
    private org.hongtu.DTO.SysTenant currentTenant;
    private String username;
    private String nickname;
    private GenderEnum gender;
    private String phone;

    /** 1 表示当前密码是一次性凭证，登录后必须修改。 */
    private Integer mustChangePassword;
    private String email;
    private String introduction;
    /** 用户所在地区。 */
    private String region;
    private Integer exp;
    private Integer level;
    private BigDecimal surplusMoney;
    private Integer points;
    private String avatar;

    /** 头像资源 ID，旧客户端继续读取 avatar。 */
    private Long avatarResourceId;
    private StatusEnum status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    private String token;
    private List<String> roleCode;
    private Long coupons;
    private Date birthday;
    private String levelName;
    private List<Long> storeIds;
    /** 店长本次会话选中的唯一门店；租户管理员为空。 */
    private Long activeStoreId;

    /**
     * 微信服务器上的唯一id
     */
    private String openId;


}
