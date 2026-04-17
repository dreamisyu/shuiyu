package com.shuiyu.game.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shuiyu.game.common.BusinessException;
import com.shuiyu.game.dto.RequestModels;
import com.shuiyu.game.dto.ResponseModels;
import com.shuiyu.game.entity.GameRoom;
import com.shuiyu.game.entity.GameRoomUser;
import com.shuiyu.game.entity.SysUser;
import com.shuiyu.game.mapper.GameRoomMapper;
import com.shuiyu.game.mapper.GameRoomUserMapper;
import com.shuiyu.game.mapper.SysUserMapper;
import com.shuiyu.game.model.GameEnums;
import com.shuiyu.game.util.RoomCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final GameRoomMapper gameRoomMapper;
    private final GameRoomUserMapper gameRoomUserMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final GameWebSocketHub webSocketHub;

    @Value("${shuiyu.ai.username-prefix}")
    private String aiUsernamePrefix;

    @Transactional
    public ResponseModels.RoomView createRoom(RequestModels.CreateRoomRequest request) {
        SysUser owner = authService.getUser(request.userId());
        GameEnums.BankerType bankerType = GameEnums.BankerType.of(request.bankerType());
        String roomCode = generateUniqueRoomCode();

        Long bankerUserId = owner.getId();
        if (bankerType == GameEnums.BankerType.AI) {
            bankerUserId = createAiUser("AI Banker").getId();
        }

        GameRoom room = new GameRoom();
        room.setRoomCode(roomCode);
        room.setHouseOwnerId(owner.getId());
        room.setBankerUserId(bankerUserId);
        room.setBankerType(bankerType.getCode());
        room.setMaxIdle(request.maxIdle());
        room.setCurrentIdle(0);
        room.setRoomStatus(GameEnums.RoomStatus.WAITING.getCode());
        gameRoomMapper.insert(room);

        insertRoomUser(room.getRoomId(), bankerUserId, GameEnums.RoomRole.BANKER, null,
                bankerType == GameEnums.BankerType.SELF ? null : GameEnums.IdleType.AI);

        int nextIdleSort = 1;
        if (bankerType == GameEnums.BankerType.AI) {
            insertRoomUser(room.getRoomId(), owner.getId(), GameEnums.RoomRole.IDLE, nextIdleSort++, GameEnums.IdleType.REAL);
        }

        List<Integer> idleTypeList = request.idleTypeList() == null ? List.of() : request.idleTypeList();
        int remaining = request.maxIdle() - (bankerType == GameEnums.BankerType.AI ? 1 : 0);
        if (remaining < 0) {
            throw new BusinessException("AI banker mode requires at least one idle seat");
        }

        for (int i = 0; i < remaining; i++) {
            Integer typeCode = i < idleTypeList.size() ? idleTypeList.get(i) : GameEnums.IdleType.REAL.getCode();
            GameEnums.IdleType idleType = GameEnums.IdleType.of(typeCode);
            if (idleType == GameEnums.IdleType.AI) {
                SysUser aiUser = createAiUser("AI Idle " + (i + 1));
                insertRoomUser(room.getRoomId(), aiUser.getId(), GameEnums.RoomRole.IDLE, nextIdleSort++, idleType);
            }
        }

        refreshCurrentIdle(room.getRoomId());
        pushEvent(room.getRoomId(), "room_changed");
        return getRoomView(room.getRoomId(), null);
    }

    @Transactional
    public ResponseModels.RoomView joinRoom(RequestModels.JoinRoomRequest request) {
        GameRoom room = gameRoomMapper.selectOne(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getRoomCode, request.roomCode()));
        if (room == null) {
            throw new BusinessException("Room does not exist");
        }
        if (room.getRoomStatus() != GameEnums.RoomStatus.WAITING.getCode()) {
            throw new BusinessException("Room has already started");
        }

        GameRoomUser existing = gameRoomUserMapper.selectOne(new LambdaQueryWrapper<GameRoomUser>()
                .eq(GameRoomUser::getRoomId, room.getRoomId())
                .eq(GameRoomUser::getUserId, request.userId()));
        if (existing != null) {
            return getRoomView(room.getRoomId(), null);
        }

        List<GameRoomUser> roomUsers = getRoomUsers(room.getRoomId());
        long idleCount = roomUsers.stream()
                .filter(item -> item.getUserRole().equals(GameEnums.RoomRole.IDLE.getCode()))
                .count();
        if (idleCount >= room.getMaxIdle()) {
            throw new BusinessException("Idle seats are full");
        }

        List<Integer> occupiedSeats = roomUsers.stream()
                .filter(item -> item.getUserRole().equals(GameEnums.RoomRole.IDLE.getCode()))
                .map(GameRoomUser::getIdleSort)
                .sorted()
                .toList();
        int idleSort = 1;
        while (occupiedSeats.contains(idleSort)) {
            idleSort++;
        }

        insertRoomUser(room.getRoomId(), request.userId(), GameEnums.RoomRole.IDLE, idleSort, GameEnums.IdleType.REAL);
        refreshCurrentIdle(room.getRoomId());
        pushEvent(room.getRoomId(), "room_changed");
        return getRoomView(room.getRoomId(), null);
    }

    @Transactional
    public void destroyRoom(RequestModels.DestroyRoomRequest request) {
        GameRoom room = gameRoomMapper.selectById(request.roomId());
        if (room == null) {
            throw new BusinessException("Room does not exist");
        }
        if (!room.getHouseOwnerId().equals(request.userId())) {
            throw new BusinessException("Only the room owner can destroy the room");
        }
        gameRoomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getRoomId, request.roomId())
                .set(GameRoom::getRoomStatus, GameEnums.RoomStatus.FINISHED.getCode())
                .set(GameRoom::getCurrentIdle, 0));
        gameRoomUserMapper.delete(new LambdaQueryWrapper<GameRoomUser>()
                .eq(GameRoomUser::getRoomId, request.roomId()));
        pushEvent(request.roomId(), "room_destroyed", "房主已解散房间");
    }

    public GameRoom getRoom(Long roomId) {
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("Room does not exist");
        }
        return room;
    }

    public List<GameRoomUser> getRoomUsers(Long roomId) {
        return gameRoomUserMapper.selectList(new LambdaQueryWrapper<GameRoomUser>()
                .eq(GameRoomUser::getRoomId, roomId));
    }

    public ResponseModels.RoomView getRoomView(Long roomId, Integer currentIdleSort) {
        GameRoom room = getRoom(roomId);
        List<GameRoomUser> roomUsers = getRoomUsers(roomId);
        Map<Long, SysUser> userMap = loadUsers(roomUsers);
        List<ResponseModels.RoomMemberView> members = roomUsers.stream()
                .sorted(Comparator.comparing(GameRoomUser::getUserRole).thenComparing(item -> item.getIdleSort() == null ? 0 : item.getIdleSort()))
                .map(item -> {
                    SysUser user = userMap.get(item.getUserId());
                    return new ResponseModels.RoomMemberView(
                            user.getId(),
                            user.getNickname(),
                            user.getAvatar(),
                            user.getUserType(),
                            item.getUserRole(),
                            item.getIdleSort(),
                            item.getIdleType(),
                            currentIdleSort != null && currentIdleSort.equals(item.getIdleSort())
                                    && item.getUserRole().equals(GameEnums.RoomRole.IDLE.getCode()),
                            true
                    );
                })
                .collect(Collectors.toList());
        return new ResponseModels.RoomView(
                room.getRoomId(),
                room.getRoomCode(),
                room.getHouseOwnerId(),
                room.getBankerUserId(),
                room.getBankerType(),
                room.getRoomStatus(),
                room.getMaxIdle(),
                room.getCurrentIdle(),
                members
        );
    }

    public Map<Long, SysUser> loadUsers(List<GameRoomUser> roomUsers) {
        if (roomUsers.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> userIds = roomUsers.stream().map(GameRoomUser::getUserId).toList();
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, item -> item));
    }

    private void insertRoomUser(Long roomId, Long userId, GameEnums.RoomRole role, Integer idleSort, GameEnums.IdleType idleType) {
        GameRoomUser roomUser = new GameRoomUser();
        roomUser.setRoomId(roomId);
        roomUser.setUserId(userId);
        roomUser.setUserRole(role.getCode());
        roomUser.setIdleSort(idleSort);
        roomUser.setIdleType(idleType == null ? null : idleType.getCode());
        gameRoomUserMapper.insert(roomUser);
    }

    private void refreshCurrentIdle(Long roomId) {
        long idleCount = getRoomUsers(roomId).stream()
                .filter(item -> item.getUserRole().equals(GameEnums.RoomRole.IDLE.getCode()))
                .count();
        gameRoomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getRoomId, roomId)
                .set(GameRoom::getCurrentIdle, (int) idleCount));
    }

    private String generateUniqueRoomCode() {
        String roomCode = RoomCodeGenerator.generate();
        while (gameRoomMapper.selectOne(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getRoomCode, roomCode)) != null) {
            roomCode = RoomCodeGenerator.generate();
        }
        return roomCode;
    }

    private SysUser createAiUser(String nickname) {
        SysUser user = new SysUser();
        user.setUsername(aiUsernamePrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        user.setPassword("");
        user.setNickname(nickname);
        user.setAvatar("");
        user.setPhone("");
        user.setUserType(GameEnums.UserType.AI.getCode());
        sysUserMapper.insert(user);
        return user;
    }

    private void pushEvent(Long roomId, String type) {
        pushEvent(roomId, type, null);
    }

    private void pushEvent(Long roomId, String type, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("roomId", roomId);
        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    webSocketHub.broadcast(roomId, payload);
                }
            });
            return;
        }
        webSocketHub.broadcast(roomId, payload);
    }
}
