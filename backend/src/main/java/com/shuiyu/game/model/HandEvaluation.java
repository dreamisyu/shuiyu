package com.shuiyu.game.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HandEvaluation {
    private GameEnums.HandRankType handRankType;
    private boolean reportedFish;
    private boolean naturalSupremeFish;
    private List<GroupStat> groups;
    private List<Integer> pairStrengths;
    private int pairSuitStrength;
    private String summary;

    @Getter
    @Builder
    public static class GroupStat {
        private List<GameCard> cards;
        private int point;
        private Integer pairStrength;
        private int suitStrength;
    }
}
