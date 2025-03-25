package com.demo.chatserver.service;

import com.corundumstudio.socketio.SocketIOClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    // 룸 ID별 정보를 캐싱하는 맵
    private final Map<String, RoomInfo> roomInfoMap = new ConcurrentHashMap<>();

    // 사용자 ID와 이름 매핑
    private final Map<String, String> userNameMap = new ConcurrentHashMap<>();

    /**
     * 룸 정보 클래스
     */
    private static class RoomInfo {
        Set<String> userIds = ConcurrentHashMap.newKeySet();
        String name;
        int userCount;

        public RoomInfo(String name) {
            this.name = name;
            this.userCount = 0;
        }
    }

    /**
     * 채팅방 참가
     */
    public boolean joinRoom(String roomId, String userId, String userName, SocketIOClient client) {
        if (roomId == null || roomId.isEmpty() || userId == null || userId.isEmpty()) {
            return false;
        }

        // 룸 정보 가져오기 (없으면 생성)
        RoomInfo roomInfo = roomInfoMap.computeIfAbsent(roomId, k -> new RoomInfo("Room " + roomId));

        // 이미 방에 있는 경우 중복 참가 방지
        if (roomInfo.userIds.contains(userId)) {
            // 기존 세션 정리 후 새 세션으로 재참가
            client.leaveRoom(roomId);
        }

        // 사용자 이름 저장
        userNameMap.put(userId, userName);

        // 방에 사용자 추가
        roomInfo.userIds.add(userId);
        roomInfo.userCount = roomInfo.userIds.size();

        // SocketIO 룸에 참가
        client.joinRoom(roomId);

        log.info("User {} joined room {}. Total users: {}", userId, roomId, roomInfo.userCount);
        return true;
    }

    /**
     * 채팅방 나가기
     */
    public boolean leaveRoom(String roomId, String userId, SocketIOClient client) {
        if (roomId == null || roomId.isEmpty() || userId == null || userId.isEmpty()) {
            return false;
        }

        RoomInfo roomInfo = roomInfoMap.get(roomId);
        if (roomInfo == null) {
            return false;
        }

        // 방에서 사용자 제거
        boolean removed = roomInfo.userIds.remove(userId);
        if (removed) {
            roomInfo.userCount = roomInfo.userIds.size();

            // SocketIO 룸에서 나가기
            client.leaveRoom(roomId);

            // 방이 비었으면 방 정보 삭제
            if (roomInfo.userCount == 0) {
                roomInfoMap.remove(roomId);
                log.info("Room {} is empty and removed", roomId);
            }

            log.info("User {} left room {}. Total users: {}", userId, roomId, roomInfo.userCount);
        }

        return removed;
    }

    /**
     * 클라이언트 연결 해제 시 모든 방에서 나가기
     */
    public void leaveAllRooms(SocketIOClient client) {
        String userId = client.getHandshakeData().getSingleUrlParam("userId");
        if (userId == null || userId.isEmpty()) {
            return;
        }

        // 사용자가 참여한 모든 방 찾기
        for (String roomId : new java.util.HashSet<>(client.getAllRooms())) {
            // 시스템 방(기본 namespace)는 건너뛰기
            if (roomId.equals(client.getNamespace().getName())) continue;

            leaveRoom(roomId, userId, client);
        }
    }

    /**
     * 사용자가 특정 룸에 있는지 확인
     */
    public boolean isUserInRoom(String roomId, String userId) {
        RoomInfo roomInfo = roomInfoMap.get(roomId);
        return roomInfo != null && roomInfo.userIds.contains(userId);
    }

    /**
     * 사용자 이름 가져오기
     */
    public String getUserName(String userId) {
        return userNameMap.get(userId);
    }
}
