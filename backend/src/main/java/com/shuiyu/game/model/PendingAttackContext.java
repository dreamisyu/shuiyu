package com.shuiyu.game.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PendingAttackContext {
    private Long roomId;
    private Long matchId;
    private Long bankerUserId;
    private Long idleUserId;
    private Integer idleSort;
    private GameEnums.AttackType attackType;
    private GameEnums.PendingStage stage;
    private GameEnums.BankerResponseType bankerResponseType;
    private LocalDateTime createdAt;
}
