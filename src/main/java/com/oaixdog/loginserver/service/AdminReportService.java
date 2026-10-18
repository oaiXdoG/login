package com.oaixdog.loginserver.service;

import com.oaixdog.adminreport.AdminReporter;
import com.oaixdog.loginserver.config.ApplicationProperties;
import com.zfoo.protocol.util.StringUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Map;

/**
 * 向管理后台上报本登录服信息（admin-report-client），规则见 docs/service-discovery.md 第 7 节。
 * 地址、密钥优先取后台托管启动时注入的环境变量，其次取 admin.report-url / admin.report-secret，都为空不上报。
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);

    private final ApplicationProperties properties;

    /**
     * HTTP 服务和 zfoo provider 启动后调用一次，之后由 AdminReporter 每 10 秒上报。
     *
     * @param port HTTP 实际监听的端口
     */
    public void start(int port) {
        String innerHost = StringUtils.isBlank(properties.getInnerHost()) ? detectInnerHost() : properties.getInnerHost();
        String host = StringUtils.isBlank(properties.getPublicHost()) ? innerHost : properties.getPublicHost();
        Map<String, Object> content = Map.of("host", host, "port", port, "innerHost", innerHost, "gmPort", 0);
        log.info("登录服已就绪，开始向管理后台上报，nodeId={}，instanceId={}，host={}，port={}，innerHost={}", properties.getNodeId(), AdminReporter.instanceId(), host, port, innerHost);
        AdminReporter.start(properties.getAppKey(), "login", properties.getNodeId(), properties.getAdminReportUrl(), properties.getAdminReportSecret(), () -> content);
    }

    @PreDestroy
    public void stop() {
        AdminReporter.stop();
    }

    /**
     * 本机第一个内网 IPv4（跳过回环、未启用和 Docker、bridge、veth 等虚拟网卡），没有时用 127.0.0.1。
     */
    private String detectInnerHost() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = nic.getName();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()
                        || name.startsWith("docker") || name.startsWith("br-") || name.startsWith("veth") || name.startsWith("virbr")) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("读取本机网卡失败", e);
        }
        return "127.0.0.1";
    }
}
