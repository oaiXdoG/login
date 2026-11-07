package com.oaixdog.loginserver.pojo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginTicketData {

    private Long userId;
    private String anchorOpenId;
    private Integer zoneId;
    /** 过期时间（毫秒时间戳），超过后票据无效，需要重新登录 */
    private Long expireAt;

    public LoginTicketData(Long userId, String anchorOpenId, Integer zoneId, Long expireAt) {
        this.userId = userId;
        this.anchorOpenId = anchorOpenId;
        this.zoneId = zoneId;
        this.expireAt = expireAt;
    }

    public static LoginTicketData valueOf(Long userId, String anchorOpenId, Integer zoneId, Long expireAt) {
        return new LoginTicketData(userId, anchorOpenId, zoneId, expireAt);
    }

}
