package org.wang.fileshare.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Handler for device discovery and communication
 */
@Slf4j
@Component
public class DiscoveryWebSocketHandler extends TextWebSocketHandler {

    // Store connected sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        String localIp = getLocalIpAddress();
        int p2pPort = 8082; // Default P2P port
        
        // Send device info to newly connected client
        String deviceInfo = String.format(
            "{\"type\":\"device_info\",\"ip\":\"%s\",\"port\":%d,\"sessionId\":\"%s\"}",
            localIp, p2pPort, session.getId()
        );
        session.sendMessage(new TextMessage(deviceInfo));
        
        log.info("New device connected: {}", session.getId());
        System.out.println("Device connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Received message: {}", message.getPayload());
        
        // Echo back for testing
        session.sendMessage(new TextMessage("{\"type\":\"echo\",\"message\":\"" + message.getPayload() + "\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("Device disconnected: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error", exception);
        if (session.isOpen()) {
            session.close();
        }
    }

    /**
     * Get local IP address for device discovery
     */
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip != null && ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get local IP", e);
        }
        return "localhost";
    }
}
