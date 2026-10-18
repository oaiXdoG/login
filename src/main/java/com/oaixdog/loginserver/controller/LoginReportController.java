package com.oaixdog.loginserver.controller;

import com.oaixdog.loginserver.service.ZoneService;
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

    private final ZoneService zoneService;

    @PacketReceiver
    public void atLoginReport(Session session, LoginReport report, SignalAttachment attachment) {
        LoginReportAnswer answer = zoneService.report(session, report);
        NetContext.getRouter().send(session, answer, attachment);
    }
}
