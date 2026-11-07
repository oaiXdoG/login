package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/**
 * 游戏服向登录服上报本进程信息，登录服回复 LoginReportAnswer。
 * 与游戏服中的同名类保持一致（ID、字段顺序和类型）。
 */
@Setter
@Getter
@Protocol(id = 5000)
public class LoginReport {

    /** 进程实例 ID，启动时生成一次，进程存活期间不变 */
    private String instanceId;
    /** 公网地址，客户端连接用 */
    private String host;
    /** 客户端连接端口（实际监听） */
    private int port;
    /** 内网地址 */
    private String innerHost;
    /** GM 接口端口（实际监听），未开放时为 0 */
    private int gmPort;

}
