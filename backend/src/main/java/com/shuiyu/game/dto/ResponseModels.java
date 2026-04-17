package com.shuiyu.game.dto;

import java.util.List;

public final class ResponseModels {
    private ResponseModels() {
    }

    public record UserProfile(
            Long id,
            String username,
            String nickname,
            String avatar,
            Integer userType
    ) {
    }

    public record AuthResponse(
            String token,
            UserProfile user
    ) {
    }

    public record RoomMemberView(
            Long userId,
            String nickname,
            String avatar,
            Integer userType,
            Integer userRole,
            Integer idleSort,
            Integer idleType,
            boolean currentTurn,
            boolean online
    ) {
    }

    public record RoomView(
            Long roomId,
            String roomCode,
            Long houseOwnerId,
            Long bankerUserId,
            Integer bankerType,
            Integer roomStatus,
            Integer maxIdle,
            Integer currentIdle,
            List<RoomMemberView> members
    ) {
    }

    public record PlayerCardsView(
            Long userId,
            String nickname,
            Integer userRole,
            Integer idleSort,
            boolean faceDown,
            boolean grouped,
            boolean reportedFish,
            boolean readyNextRound,
            Integer fishType,
            List<String> handCards,
            List<String> group1,
            List<String> group2,
            String changeCard1,
            String changeCard2
    ) {
    }

    public record PendingActionView(
            String stage,
            String attackType,
            Long bankerUserId,
            Long idleUserId,
            String idleNickname,
            Integer idleSort,
            String message
    ) {
    }

    public record RoundSettleView(
            Long idleUserId,
            String idleNickname,
            Integer idleSort,
            Integer attackType,
            Integer bankerResponse,
            Integer result,
            Integer rate,
            Long drinkUserId,
            String settleTime
    ) {
    }

    public record GlobalSettleView(
            Integer bankerWinCount,
            Integer idleWinCount,
            Integer drawCount,
            Integer maxRate,
            String settleTime,
            String settleDetail
    ) {
    }

    public record GameStateView(
            RoomView room,
            Long matchId,
            Integer matchStatus,
            Integer currentIdleSort,
            boolean bankerGroupLocked,
            List<PlayerCardsView> players,
            List<RoundSettleView> roundSettles,
            GlobalSettleView globalSettle,
            List<PendingActionView> pendingActions,
            PendingActionView pendingAction
    ) {
    }

    public record HistoryItemView(
            Long matchId,
            String roomCode,
            String startTime,
            String endTime,
            Integer bankerWinCount,
            Integer idleWinCount,
            Integer drawCount,
            Integer maxRate
    ) {
    }

    public record HistoryDetailView(
            GameStateView latestState,
            List<RoundSettleView> roundSettles,
            GlobalSettleView globalSettle
    ) {
    }
}
