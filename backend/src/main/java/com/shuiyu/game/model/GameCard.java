package com.shuiyu.game.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
public class GameCard {
    public static final String SPADE = "\u9ed1\u6843";
    public static final String HEART = "\u7ea2\u6843";
    public static final String CLUB = "\u6885\u82b1";
    public static final String DIAMOND = "\u65b9\u5757";
    public static final String NONE = "\u65e0";

    private String cardId;
    private String suit;
    private String rankLabel;
    private int rankValue;
    private int pointValue;
    private boolean joker;
    private String jokerName;
    private String assignedRankLabel;

    public GameCard copy() {
        return this.toBuilder().build();
    }

    public String getEffectiveRankLabel() {
        return assignedRankLabel != null ? assignedRankLabel : rankLabel;
    }

    public int getEffectiveRankStrength() {
        return rankStrength(getEffectiveRankLabel());
    }

    public int getEffectivePointValue() {
        return pointByRank(getEffectiveRankLabel());
    }

    public int getSuitStrength() {
        return suitStrength(suit);
    }

    public static int pointByRank(String rankLabel) {
        return switch (rankLabel) {
            case "A" -> 1;
            case "J", "Q", "K", "10" -> 10;
            default -> Integer.parseInt(rankLabel);
        };
    }

    public static int rankStrength(String rankLabel) {
        return switch (rankLabel) {
            case "A" -> 14;
            case "K" -> 13;
            case "Q" -> 12;
            case "J" -> 11;
            default -> Integer.parseInt(rankLabel);
        };
    }

    public static int suitStrength(String suit) {
        return switch (suit) {
            case SPADE -> 4;
            case HEART -> 3;
            case CLUB -> 2;
            case DIAMOND -> 1;
            default -> 0;
        };
    }
}
