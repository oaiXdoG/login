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
    /** 抖音直播间 id。本地测试登录为空。游戏服用来把回调对上这台客户端 */
    private String roomId;
    /** 过期时间（毫秒时间戳），超过后票据无效，需要重新登录 */
    private Long expireAt;

    public LoginTicketData(Long userId, String anchorOpenId, String roomId, Long expireAt) {
        this.userId = userId;
        this.anchorOpenId = anchorOpenId;
        this.roomId = roomId;
        this.expireAt = expireAt;
    }

    public static LoginTicketData valueOf(Long userId, String anchorOpenId, String roomId, Long expireAt) {
        return new LoginTicketData(userId, anchorOpenId, roomId, expireAt);
    }

}
