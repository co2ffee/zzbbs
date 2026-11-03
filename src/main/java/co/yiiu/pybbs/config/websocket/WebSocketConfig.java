package co.yiiu.pybbs.config.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig {

    // MockMvc 的 mock servlet 环境里没有 javax.websocket.server.ServerContainer，测试 profile 下不注册
    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public ServerEndpointExporter serverEndpoint() {
        return new ServerEndpointExporter();
    }

}
