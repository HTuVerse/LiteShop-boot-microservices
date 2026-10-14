package org.hongtu.VO;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 管理端动态口令二次验证挑战；验证通过前不创建正式登录会话。 */
@Data
@AllArgsConstructor
public class AdminTotpChallengeVO {

    private boolean twoFactorRequired;
    private String challengeTicket;
    private int expiresInSeconds;
}
