package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.GenderEnum;
import org.hongtu.enums.StatusEnum;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 系统用户表
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 登录账号
     */
    @TableField("username")
    private String username;

    /**
     * 密码
     */
    @TableField("password")
    @JsonIgnore
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 用户昵称
     */
    @TableField("nickname")
    private String nickname;


    /**
     * 微信小程序用户唯一标识符
     */
    @TableField("open_id")
    @JsonIgnore
    private String openId;

    /**
     * 管理员动态口令密钥。只用于登录校验，不进入任何响应。
     */
    @TableField("totp_secret")
    @JsonIgnore
    private String totpSecret;

    /**
     * 1 表示必须先修改一次性初始密码。
     */
    @TableField("must_change_password")
    private Integer mustChangePassword;

    /**
     * 租户ID（多租户）
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 性别[1:男，2:女，0:保密]
     */
    @TableField("gender")
    private GenderEnum gender;

    /**
     * 电话
     */
    @TableField("phone")
    private String phone;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 用户介绍
     */
    @TableField("introduction")
    private String introduction;

    /**
     * 用户经验值
     */
    @TableField("exp")
    private Integer exp;

    /**
     * 用户等级
     */
    @TableField("level")
    private Integer level;

    /**
     * 用户生日
     */
    @TableField("birthday")
    private Date birthday;

    /**
     * 用户积分
     */
    @TableField("points")
    private Integer points;


    /**
     * 用户余额
     */
    @TableField("surplus_money")
    private BigDecimal surplusMoney;

    /**
     * 头像地址
     */
    @TableField("avatar")
    private String avatar;

    /** 头像对应的资源库 ID；历史外部头像仍使用 avatar。 */
    @TableField(exist = false)
    private Long avatarResourceId;

    /**
     * 状态（1-正常 0-禁用）
     */
    @TableField("status")
    private StatusEnum status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<String> roleCode;

}
