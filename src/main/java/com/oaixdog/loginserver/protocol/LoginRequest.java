package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Protocol(id = 5002)
public class LoginRequest {

    /** 区服 ID，与游戏服上报的 serverId 一致；单区时客户端固定传这一个值 */
    private Integer serverId;
    private String platform;
    private String account;
    private String token;
    private String clientVersion;
    private String resourceVersion;

}
