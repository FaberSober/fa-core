package com.faber.core.config.websocket;

import jakarta.websocket.Session;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket client info entity
 */
@Data
public class ClientInfoEntity {

    /**
     * 客户端唯一标识
     */
    private String token;

    /**
     * 客户端连接的session
     */
    private Session session;

    /**
     * 连接存活时间
     */
    private LocalDateTime existTime;

}
