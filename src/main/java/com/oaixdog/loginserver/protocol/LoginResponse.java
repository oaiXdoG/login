package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Protocol(id = 5004)
public class LoginResponse {

    private String loginTicket;
    private String host;
    private int port;

    public static LoginResponse valueOf(String loginTicket, String host, int port) {
        LoginResponse response = new LoginResponse();
        response.loginTicket = loginTicket;
        response.host = host;
        response.port = port;
        return response;
    }

}
