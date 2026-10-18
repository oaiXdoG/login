package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Protocol(id = 5002)
public class LoginRequest {

    private String platform;
    private String account;
    private String token;
    private String clientVersion;
    private String resourceVersion;

}
