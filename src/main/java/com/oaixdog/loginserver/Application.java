package com.oaixdog.loginserver;

import com.oaixdog.loginserver.config.ApplicationProperties;
import com.oaixdog.loginserver.protocol.LoginRequest;
import com.oaixdog.loginserver.protocol.VersionRequest;
import com.zfoo.net.core.HostAndPort;
import com.zfoo.net.core.http.HttpServer;
import com.zfoo.net.packet.DecodedPacketInfo;
import com.zfoo.net.router.attachment.HttpAttachment;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.ThreadUtils;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class Application {
    public static void main(String[] args) throws IOException {
        // 启动参数 -Dkey=value 可以覆盖 application.properties
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[]{"config.xml"}, false);
        context.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        context.refresh();

        ApplicationProperties properties = context.getBean(ApplicationProperties.class);
        context.registerShutdownHook();

        Function<FullHttpRequest, DecodedPacketInfo> requestResolver = rawRequest -> {
            HttpAttachment attachment = HttpAttachment.valueOf(rawRequest, HttpResponseStatus.OK);
            QueryStringDecoder uri = new QueryStringDecoder(rawRequest.uri());
            String route = rawRequest.method().name() + " " + uri.path();

            Object request = switch (route) {
                case "POST /api/login" -> parseJsonBody(rawRequest, LoginRequest.class);
                case "POST /api/version" -> parseJsonBody(rawRequest, VersionRequest.class);
                default -> new RuntimeException("Unknown route: " + route);
            };

            return DecodedPacketInfo.valueOf(request, attachment);
        };

        HttpServer server = new HttpServer(HostAndPort.valueOf(properties.getHost(), properties.getPort()), requestResolver);

        server.start();
        ThreadUtils.sleep(Long.MAX_VALUE);
    }

    private static <T> T parseJsonBody(FullHttpRequest rawRequest, Class<T> requestClass) {
        String body = rawRequest.content().toString(StandardCharsets.UTF_8);
        return JsonUtils.string2Object(body, requestClass);
    }
}
