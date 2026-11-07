package com.oaixdog.loginserver.service;

import com.oaixdog.protocol.admin.ServerReport;
import com.zfoo.event.anno.EventReceiver;
import com.zfoo.net.NetContext;
import com.zfoo.net.consumer.event.ConsumerStartEvent;
import com.zfoo.net.packet.common.Message;
import com.zfoo.net.session.Session;
import com.zfoo.net.util.SessionUtils;
import com.zfoo.protocol.exception.RunException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 每连上一台管理后台（provider admin）就上报一次服务类型和区服 ID；管理后台重启后 zfoo 重新连上，会再次触发。
 */
@Service
public class AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);

    private static final ServerReport REPORT = ServerReport.valueOf("login", 0);

    @EventReceiver
    public void onConsumerStartEvent(ConsumerStartEvent event) {
        Session session = event.getSession();
        if (!SessionUtils.isActive(session) || !isAdmin(session)) {
            return;
        }
        String nodeId = session.getConsumerRegister().getId();
        NetContext.getRouter().asyncAsk(session, REPORT, Message.class, null)
                .notComplete(() -> log.warn("向管理后台上报失败或超时，节点={}", nodeId))
                .whenComplete(answer -> {
                    if (answer.getCode() == 1) {
                        log.info("已向管理后台上报，节点={}", nodeId);
                    } else {
                        log.error("管理后台拒绝上报，节点={}，原因：{}", nodeId, answer.getMessage());
                    }
                });
    }

    /**
     * zfoo 按协议所属模块匹配对方的 protocol-module，能处理 ServerReport 的就是管理后台；没有任何管理后台连接时抛 RunException。
     */
    private static boolean isAdmin(Session session) {
        try {
            return NetContext.getConsumer().findProviders(REPORT).contains(session);
        } catch (RunException e) {
            return false;
        }
    }
}
