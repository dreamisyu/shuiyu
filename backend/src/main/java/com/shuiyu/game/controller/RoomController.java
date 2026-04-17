package com.shuiyu.game.controller;

import com.shuiyu.game.common.ApiResponse;
import com.shuiyu.game.dto.RequestModels;
import com.shuiyu.game.dto.ResponseModels;
import com.shuiyu.game.service.GameService;
import com.shuiyu.game.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/room")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
    private final GameService gameService;

    @PostMapping("/create")
    public ApiResponse<ResponseModels.RoomView> create(@Valid @RequestBody RequestModels.CreateRoomRequest request) {
        ResponseModels.RoomView room = roomService.createRoom(request);
        gameService.tryAutoStartAiRoom(room.roomId());
        return ApiResponse.success(room);
    }

    @PostMapping("/join")
    public ApiResponse<ResponseModels.RoomView> join(@Valid @RequestBody RequestModels.JoinRoomRequest request) {
        ResponseModels.RoomView room = roomService.joinRoom(request);
        gameService.tryAutoStartAiRoom(room.roomId());
        return ApiResponse.success(room);
    }

    @PostMapping("/destroy")
    public ApiResponse<Void> destroy(@Valid @RequestBody RequestModels.DestroyRoomRequest request) {
        roomService.destroyRoom(request);
        return ApiResponse.success("房间已解散", null);
    }
}
