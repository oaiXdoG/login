package com.oaixdog.loginserver.service;

import com.oaixdog.protocol.log.EventReport;
import com.zfoo.net.NetContext;
import com.zfoo.protocol.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件日志：以 consumer 身份把 EventReport 发给 log 服务（provider log），不应答。
 * 字段必须与 log 中该事件的定义一致（多出的字段整条导入失败），见 log 工程 AGENTS.md；值为 null 的字段不上报。
 * 上报失败（没有连上 log 等）只打日志，不影响业务。
 */
@Service
public class EventReportService {

    private static final Logger log = LoggerFactory.getLogger(EventReportService.class);

    /**
     * @param fields 依次为 key、value；自动加上 time（上报时间，毫秒）
     */
    public void report(String event, Object... fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", System.currentTimeMillis());
        for (int i = 0; i + 1 < fields.length; i += 2) {
            if (fields[i + 1] != null) {
                data.put((String) fields[i], fields[i + 1]);
            }
        }
        String json = JsonUtils.object2String(data);
        try {
            NetContext.getConsumer().send(EventReport.valueOf(event, json), null);
        } catch (RuntimeException e) {
            log.warn("事件上报失败，event={}，data={}，原因：{}", event, json, e.getMessage());
        }
    }
}
