package com.demo.chatserver.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@Slf4j
@org.springframework.context.annotation.Configuration
@RequiredArgsConstructor
public class SocketIOConfig {

    @Value("${socketio.port}")
    private int port;

    @Value("${socketio.host}")
    private String host;

    @Value("${socketio.ping-interval}")
    private int pingInterval;

    @Value("${socketio.ping-timeout}")
    private int pingTimeout;

    @Value("${socketio.cors-origin}")
    private String corsOrigin;

    private SocketIOServer server;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setPingInterval(pingInterval);
        config.setPingTimeout(pingTimeout);
        config.setOrigin(corsOrigin);

        // 서버 인스턴스 생성
        server = new SocketIOServer(config);
        return server;
    }

    @PreDestroy
    public void stopSocketIOServer() {
        if (server != null) {
            server.stop();
            log.info("SocketIO server stopped");
        }
    }
}
