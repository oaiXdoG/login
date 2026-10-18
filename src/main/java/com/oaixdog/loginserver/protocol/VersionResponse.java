package com.oaixdog.loginserver.protocol;

import com.zfoo.protocol.anno.Protocol;

@Protocol(id = 5005)
public class VersionResponse {

    private String serverClientVersion;
    private String serverResourceVersion;
    private boolean needClientUpdate;
    private boolean needResourceUpdate;

    public static VersionResponse valueOf(String serverClientVersion, String serverResourceVersion, boolean needClientUpdate, boolean needResourceUpdate) {
        VersionResponse response = new VersionResponse();
        response.serverClientVersion = serverClientVersion;
        response.serverResourceVersion = serverResourceVersion;
        response.needClientUpdate = needClientUpdate;
        response.needResourceUpdate = needResourceUpdate;
        return response;
    }

    public String getServerClientVersion() {
        return serverClientVersion;
    }

    public void setServerClientVersion(String serverClientVersion) {
        this.serverClientVersion = serverClientVersion;
    }

    public String getServerResourceVersion() {
        return serverResourceVersion;
    }

    public void setServerResourceVersion(String serverResourceVersion) {
        this.serverResourceVersion = serverResourceVersion;
    }

    public boolean isNeedClientUpdate() {
        return needClientUpdate;
    }

    public void setNeedClientUpdate(boolean needClientUpdate) {
        this.needClientUpdate = needClientUpdate;
    }

    public boolean isNeedResourceUpdate() {
        return needResourceUpdate;
    }

    public void setNeedResourceUpdate(boolean needResourceUpdate) {
        this.needResourceUpdate = needResourceUpdate;
    }
}
