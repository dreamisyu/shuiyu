package com.shuiyu.game.model;

import com.shuiyu.game.common.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public final class GameEnums {
    private GameEnums() {
    }

    @Getter
    @RequiredArgsConstructor
    public enum UserType {
        REAL(1, "REAL_USER"),
        AI(2, "AI_USER");

        private final int code;
        private final String label;

        public static UserType of(Integer code) {
            for (UserType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new BusinessException("Invalid user type");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum BankerType {
        SELF(1, "SELF"),
        AI(2, "AI");

        private final int code;
        private final String label;

        public static BankerType of(Integer code) {
            for (BankerType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new BusinessException("Invalid banker type");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum RoomStatus {
        WAITING(0, "WAITING"),
        PLAYING(1, "PLAYING"),
        FINISHED(2, "FINISHED");

        private final int code;
        private final String label;
    }

    @Getter
    @RequiredArgsConstructor
    public enum MatchStatus {
        NOT_DEALT(0, "NOT_DEALT"),
        DEALT(1, "DEALT"),
        GROUPING(2, "GROUPING"),
        COMPARING(3, "COMPARING"),
        FINISHED(4, "FINISHED");

        private final int code;
        private final String label;
    }

    @Getter
    @RequiredArgsConstructor
    public enum RoomRole {
        BANKER(1, "BANKER"),
        IDLE(2, "IDLE");

        private final int code;
        private final String label;
    }

    @Getter
    @RequiredArgsConstructor
    public enum IdleType {
        REAL(1, "REAL"),
        AI(2, "AI");

        private final int code;
        private final String label;

        public static IdleType of(Integer code) {
            for (IdleType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new BusinessException("Invalid idle type");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum FishType {
        NORMAL(1, "NORMAL"),
        SUPREME(2, "SUPREME");

        private final int code;
        private final String label;

        public static FishType of(Integer code) {
            for (FishType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new BusinessException("Invalid fish type");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum AttackType {
        STRONG(1, "STRONG"),
        ASK_RUN(2, "ASK_RUN"),
        HIDDEN(3, "HIDDEN"),
        FISH(4, "FISH");

        private final int code;
        private final String label;

        public static AttackType of(Integer code) {
            for (AttackType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new BusinessException("Invalid attack type");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum BankerResponseType {
        GIVE_WAY(1, "GIVE_WAY"),
        CALL_KILL(2, "CALL_KILL"),
        OPEN(3, "OPEN"),
        NOT_OPEN(4, "NOT_OPEN");

        private final int code;
        private final String label;

        public static BankerResponseType of(Integer code) {
            for (BankerResponseType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new BusinessException("Invalid banker response");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum RoundResultType {
        BANKER_WIN(1, "BANKER_WIN"),
        IDLE_WIN(2, "IDLE_WIN"),
        DRAW(3, "DRAW");

        private final int code;
        private final String label;
    }

    @Getter
    @RequiredArgsConstructor
    public enum HandRankType {
        MAMA_KILL(10, "MAMA_KILL"),
        SUPREME_FISH(9, "SUPREME_FISH"),
        NORMAL_FISH(8, "NORMAL_FISH"),
        PAIR(7, "PAIR"),
        POINT(6, "POINT");

        private final int priority;
        private final String label;
    }

    @Getter
    @RequiredArgsConstructor
    public enum PendingStage {
        WAIT_BANKER_RESPONSE("WAIT_BANKER_RESPONSE"),
        WAIT_IDLE_DECISION("WAIT_IDLE_DECISION"),
        WAIT_BANKER_GROUP("WAIT_BANKER_GROUP");

        private final String label;
    }
}
