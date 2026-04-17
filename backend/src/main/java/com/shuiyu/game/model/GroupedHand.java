package com.shuiyu.game.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GroupedHand {
    private List<GameCard> allCards;
    private List<GameCard> group1;
    private List<GameCard> group2;
    private String changeCard1;
    private String changeCard2;
}
