package com.oaixdog.loginserver.controller;

import com.oaixdog.loginserver.service.GameOnlineService;
import com.oaixdog.loginserver.protocol.LoginReport;
import com.oaixdog.loginserver.protocol.LoginReportAnswer;
import com.zfoo.net.NetContext;
import com.zfoo.net.anno.PacketReceiver;
import com.zfoo.net.router.attachment.SignalAttachment;
import com.zfoo.net.session.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginReportController {

    private final GameOnlineService gameOnlineService;

    @PacketReceiver
    public void atLoginReport(Session session, LoginReport report, SignalAttachment attachment) {
        LoginReportAnswer answer = gameOnlineService.report(session, report);
        NetContext.getRouter().send(session, answer, attachment);
    }
}
