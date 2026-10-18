package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/**
 * 游戏服 → 登录服：上报游戏服信息，登录服回复 LoginReportAnswer。属于登录服的 login 模块，与游戏服中的同名类保持一致（ID、字段顺序和类型），见 docs/service-discovery.md；字段只增不改。
 */
@Setter
@Getter
@Protocol(id = 5000)
public class LoginReport {

    private int serverId;
    private String instanceId;
    private String host;
    private int port;
    private String innerHost;
    private int gmPort;

}
