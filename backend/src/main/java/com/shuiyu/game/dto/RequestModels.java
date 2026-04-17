package com.shuiyu.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class RequestModels {
    private RequestModels() {
    }

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record CreateRoomRequest(
            @NotNull Long userId,
            @NotNull Integer bankerType,
            @NotNull @Min(1) @Max(10) Integer maxIdle,
            List<Integer> idleTypeList
    ) {
    }

    public record JoinRoomRequest(
            @NotBlank String roomCode,
            @NotNull Long userId
    ) {
    }

    public record DestroyRoomRequest(
            @NotNull Long roomId,
            @NotNull Long userId
    ) {
    }

    public record GroupRequest(
            @NotNull Long roomId,
            @NotNull Long userId,
            @NotEmpty List<String> group1,
            @NotEmpty List<String> group2,
            String changeCard1,
            String changeCard2
    ) {
    }

    public record ReportFishRequest(
            @NotNull Long roomId,
            @NotNull Long userId,
            @NotNull Integer fishType
    ) {
    }

    public record AttackRequest(
            @NotNull Long roomId,
            @NotNull Long userId,
            @NotNull Integer attackType,
            Integer bankerResponse,
            Boolean acceptCallKill
    ) {
    }

    public record BankerResponseRequest(
            @NotNull Long roomId,
            @NotNull Long userId,
            @NotNull Long idleUserId,
            @NotNull Integer bankerResponse
    ) {
    }

    public record IdleDecisionRequest(
            @NotNull Long roomId,
            @NotNull Long userId,
            @NotNull Boolean acceptCallKill
    ) {
    }

    public record ProcessPendingRequest(
            @NotNull Long roomId,
            @NotNull Long userId,
            @NotNull Long idleUserId
    ) {
    }

    public record ReadyNextRoundRequest(
            @NotNull Long roomId,
            @NotNull Long userId
    ) {
    }
}
