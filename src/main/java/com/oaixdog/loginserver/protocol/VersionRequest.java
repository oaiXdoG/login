package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Protocol(id = 5003)
public class VersionRequest {

    private String clientVersion;
    private String resourceVersion;

}
