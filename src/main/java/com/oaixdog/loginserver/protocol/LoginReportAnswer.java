package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录服 → 游戏服：LoginReport 是否接受，拒绝时 message 为原因，接受时为空字符串。
 */
@Setter
@Getter
@Protocol(id = 5001)
public class LoginReportAnswer {

    private boolean accepted;
    private String message;

    public static LoginReportAnswer valueOf(boolean accepted, String message) {
        LoginReportAnswer answer = new LoginReportAnswer();
        answer.accepted = accepted;
        answer.message = message;
        return answer;
    }

}
