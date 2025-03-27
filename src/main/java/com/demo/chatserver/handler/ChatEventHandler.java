package com.demo.chatserver.handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.demo.chatserver.proto.ChatMessage;
import com.demo.chatserver.proto.JoinRoomRequest;
import com.demo.chatserver.proto.JoinRoomResponse;
import com.demo.chatserver.service.ChatRoomService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventHandler {

    private final ChatRoomService chatRoomService;
    private final SocketIOServer server;

    @PostConstruct
    public void init() {
        server.addListeners(this);
        server.start();
    }

    @OnConnect
    public void onConnected(SocketIOClient client) {
        String sessionId = client.getSessionId().toString();
        String userId = getUserIdFromClient(client);

        log.info("Client connected - sessionId: {}, userId: {}", sessionId, userId);
    }

    @OnDisconnect
    public void onDisconnected(SocketIOClient client) {
        String sessionId = client.getSessionId().toString();
        String userId = getUserIdFromClient(client);

        // 사용자의 현재 참여 중인 모든 룸에서 나가기 처리
        chatRoomService.leaveAllRooms(client);

        log.info("Client disconnected - sessionId: {}, userId: {}", sessionId, userId);
    }

    @OnEvent("chat:message")
    public void onChatMessage(SocketIOClient client, byte[] data, AckRequest ackRequest) {
        try {
            log.info("Client received chat message: {}", data);
            // Base64 디코딩
            /*byte[] messageBytes = Base64.getDecoder().decode(data);
            ChatMessage message = ChatMessage.parseFrom(messageBytes);*/
            ChatMessage message = ChatMessage.parseFrom(data);

            log.info("Received message from {} in room {}: {}",
                    message.getSenderName(), message.getRoomId(), message.getContent());

            // 메시지 유효성 검사
            String roomId = message.getRoomId();

            if (roomId == null || roomId.isEmpty()) {
                log.warn("Invalid message received: missing roomId");
                return;
            }

            // 메시지 브로드캐스트
            broadcastBinaryMessageToRoom(message);

            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("success");
            }
        } catch (Exception e) {
            log.error("Error handling chat message: {}", e.getMessage(), e);
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("error", e.getMessage());
            }
        }
    }

    @OnEvent("chat:joinRoom")
    public void onJoinRoom(SocketIOClient client, String data, AckRequest ackRequest) {
        try {
            log.info("Client received join message: {}", data);
            // Base64 디코딩
            byte[] requestBytes = Base64.getDecoder().decode(data);
            JoinRoomRequest request = JoinRoomRequest.parseFrom(requestBytes);

            String roomId = request.getRoomId();
            String userId = request.getUserId();
            String userName = request.getUserName();

            log.info("User {} ({}) joining room {}", userId, userName, roomId);

            // 룸 참가 처리
            boolean joined = chatRoomService.joinRoom(roomId, userId, userName, client);

            // 응답 생성
            JoinRoomResponse.Builder responseBuilder = JoinRoomResponse.newBuilder()
                    .setSuccess(joined)
                    .setRoomId(roomId);

            if (joined) {
                // 최근 메시지는 없는 상태로 빈 리스트 반환 (실제로는 DB나 캐시에서 가져올 수 있음)
                List<ChatMessage> recentMessages = new ArrayList<>();
                responseBuilder.addAllRecentMessages(recentMessages);

                // 시스템 메시지 생성 (새 사용자 입장)
                ChatMessage systemMessage = ChatMessage.newBuilder()
                        .setId(java.util.UUID.randomUUID().toString())
                        .setRoomId(roomId)
                        .setSenderId("system")
                        .setSenderName("System")
                        .setContent(userName + " has joined the room")
                        .setTimestamp(System.currentTimeMillis())
                        .setType(ChatMessage.MessageType.SYSTEM)
                        .build();

                broadcastBinaryMessageToRoom(systemMessage);
            }

            // 응답 전송
            /*JoinRoomResponse response = responseBuilder.build();
            byte[] responseBytes = response.toByteArray();
            String responseBase64 = Base64.getEncoder().encodeToString(responseBytes);

            client.sendEvent("chat:joinRoom", responseBase64);*/
        } catch (Exception e) {
            log.error("Error handling join room: {}", e.getMessage(), e);
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("error", e.getMessage());
            }
        }
    }

    @OnEvent("chat:leaveRoom")
    public void onLeaveRoom(SocketIOClient client, String roomId, AckRequest ackRequest) {
        try {
            String userId = getUserIdFromClient(client);
            if (userId == null) return;

            log.info("User {} leaving room {}", userId, roomId);

            // 룸에서 나가기 처리
            String userName = chatRoomService.getUserName(userId);
            boolean left = chatRoomService.leaveRoom(roomId, userId, client);

            if (left && userName != null) {
                // 시스템 메시지 생성 (사용자 퇴장)
                ChatMessage systemMessage = ChatMessage.newBuilder()
                        .setId(java.util.UUID.randomUUID().toString())
                        .setRoomId(roomId)
                        .setSenderId("system")
                        .setSenderName("System")
                        .setContent(userName + " has left the room")
                        .setTimestamp(System.currentTimeMillis())
                        .setType(ChatMessage.MessageType.SYSTEM)
                        .build();

                broadcastBinaryMessageToRoom(systemMessage);
            }
        } catch (Exception e) {
            log.error("Error handling leave room: {}", e.getMessage(), e);
            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("error", e.getMessage());
            }
        }
    }

    // 문자열 메시지
    private void broadcastMessageToRoom(ChatMessage message) {
        String roomId = message.getRoomId();
        byte[] messageBytes = message.toByteArray();
        String messageBase64 = Base64.getEncoder().encodeToString(messageBytes);

        // 룸에 있는 모든 클라이언트에게 메시지 전송
        log.info("Broadcast message to {} in room {}", messageBase64, roomId);
        server.getRoomOperations(roomId).sendEvent("chat:message", messageBase64);
    }

    // 바이너리 메시지
    private void broadcastBinaryMessageToRoom(ChatMessage message) {
        String roomId = message.getRoomId();
        byte[] messageBytes = message.toByteArray();

        // 룸에 있는 모든 클라이언트에게 바이너리 메시지 전송
        log.info("Broadcast binary message to room {}, size: {} bytes", roomId, messageBytes.length);
        server.getRoomOperations(roomId).getClients()
                .forEach(c-> c.sendEvent("chat:message", messageBytes));
    }

    private String getUserIdFromClient(SocketIOClient client) {
        // 클라이언트의 핸드쉐이크 데이터에서 userId 추출
        return client.getHandshakeData().getSingleUrlParam("userId");
    }
}
