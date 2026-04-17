package com.shuiyu.game.service;

import com.shuiyu.game.common.BusinessException;
import com.shuiyu.game.model.GameCard;
import com.shuiyu.game.model.GameEnums;
import com.shuiyu.game.model.GroupedHand;
import com.shuiyu.game.model.HandEvaluation;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GameRuleEngine {
    private static final String SPADE = GameCard.SPADE;
    private static final String HEART = GameCard.HEART;
    private static final String CLUB = GameCard.CLUB;
    private static final String DIAMOND = GameCard.DIAMOND;
    private static final String NONE = GameCard.NONE;
    private static final String BIG_JOKER = "\u5927\u738b";
    private static final String SMALL_JOKER = "\u5c0f\u738b";
    private static final String WHITE_CARD = "\u767d\u724c";

    private static final List<String> SUITS = List.of(SPADE, HEART, CLUB, DIAMOND);
    private static final List<String> RANKS = List.of("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");
    private static final int[][] GROUP_PAIRINGS = {
            {0, 1, 2, 3},
            {0, 2, 1, 3},
            {0, 3, 1, 2}
    };

    private final Random random = new SecureRandom();

    public List<GameCard> buildDeck() {
        List<GameCard> cards = new ArrayList<>();
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                cards.add(normalCard(suit, rank));
            }
        }
        cards.add(joker(BIG_JOKER));
        cards.add(joker(SMALL_JOKER));
        cards.add(joker(WHITE_CARD));
        Collections.shuffle(cards, random);
        return cards;
    }

    public List<GameCard> parseCards(String cardsText) {
        if (cardsText == null || cardsText.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(cardsText.split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(this::parseCard)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public String serializeCards(List<GameCard> cards) {
        return cards.stream().map(GameCard::getCardId).collect(Collectors.joining(","));
    }

    public GroupedHand validateAndBuildGrouping(List<GameCard> handCards,
                                                List<String> group1Ids,
                                                List<String> group2Ids,
                                                String changeCard1,
                                                String changeCard2) {
        if (group1Ids == null || group2Ids == null || group1Ids.size() != 2 || group2Ids.size() != 2) {
            throw new BusinessException("Need exactly 2 groups and 2 cards in each group");
        }
        Set<String> source = handCards.stream().map(GameCard::getCardId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> target = new LinkedHashSet<>();
        target.addAll(group1Ids);
        target.addAll(group2Ids);
        if (target.size() != 4 || !source.equals(target)) {
            throw new BusinessException("Grouped cards must match the original hand");
        }

        Map<String, GameCard> cardMap = handCards.stream()
                .collect(Collectors.toMap(GameCard::getCardId, GameCard::copy, (left, right) -> left, HashMap::new));
        List<GameCard> group1 = group1Ids.stream().map(cardMap::get).map(GameCard::copy).toList();
        List<GameCard> group2 = group2Ids.stream().map(cardMap::get).map(GameCard::copy).toList();
        String resolvedChangeCard1 = resolveChangeCard(group1, changeCard1, handCards);
        String resolvedChangeCard2 = resolveChangeCard(group2, changeCard2, handCards);

        List<GameCard> allCards = new ArrayList<>();
        allCards.addAll(group1);
        allCards.addAll(group2);
        return GroupedHand.builder()
                .allCards(allCards)
                .group1(group1)
                .group2(group2)
                .changeCard1(resolvedChangeCard1)
                .changeCard2(resolvedChangeCard2)
                .build();
    }

    public GroupedHand chooseBestGrouping(List<GameCard> handCards) {
        if (handCards.size() != 4) {
            throw new BusinessException("Grouping requires exactly 4 cards");
        }
        GroupedHand bestGrouping = null;
        HandEvaluation bestEvaluation = null;
        for (int[] pairing : GROUP_PAIRINGS) {
            List<String> group1Ids = List.of(
                    handCards.get(pairing[0]).getCardId(),
                    handCards.get(pairing[1]).getCardId()
            );
            List<String> group2Ids = List.of(
                    handCards.get(pairing[2]).getCardId(),
                    handCards.get(pairing[3]).getCardId()
            );
            GroupedHand candidate = validateAndBuildGrouping(handCards, group1Ids, group2Ids, null, null);
            GameEnums.FishType fishType = detectFishType(candidate);
            HandEvaluation evaluation = evaluate(candidate, fishType != null, fishType == null ? null : fishType.getCode());
            if (bestEvaluation == null || compareHands(evaluation, bestEvaluation) > 0) {
                bestGrouping = candidate;
                bestEvaluation = evaluation;
            }
        }
        if (bestGrouping == null) {
            throw new BusinessException("Unable to build a valid grouping for AI");
        }
        return bestGrouping;
    }

    public GameEnums.FishType detectFishType(GroupedHand groupedHand) {
        List<String> effectiveRanks = groupedHand.getAllCards().stream()
                .map(GameCard::getEffectiveRankLabel)
                .toList();
        boolean allSameRank = new LinkedHashSet<>(effectiveRanks).size() == 1;
        if (allSameRank) {
            return GameEnums.FishType.SUPREME;
        }
        HandEvaluation.GroupStat first = buildGroupStat(groupedHand.getGroup1());
        HandEvaluation.GroupStat second = buildGroupStat(groupedHand.getGroup2());
        boolean twoPairs = first.getPairStrength() != null
                && second.getPairStrength() != null
                && !first.getPairStrength().equals(second.getPairStrength());
        return twoPairs ? GameEnums.FishType.NORMAL : null;
    }

    public HandEvaluation evaluate(GroupedHand groupedHand, boolean reportedFish, Integer fishTypeCode) {
        HandEvaluation.GroupStat first = buildGroupStat(groupedHand.getGroup1());
        HandEvaluation.GroupStat second = buildGroupStat(groupedHand.getGroup2());
        List<HandEvaluation.GroupStat> groups = new ArrayList<>(List.of(first, second));
        groups.sort(Comparator.comparingInt(HandEvaluation.GroupStat::getPoint)
                .thenComparingInt(HandEvaluation.GroupStat::getSuitStrength)
                .reversed());

        boolean mamaKill = groups.stream().allMatch(group -> group.getPoint() == 0);
        GameEnums.FishType intrinsicFish = detectFishType(groupedHand);
        boolean naturalSupreme = intrinsicFish == GameEnums.FishType.SUPREME
                && groupedHand.getAllCards().stream().noneMatch(GameCard::isJoker);
        List<Integer> pairStrengths = groups.stream()
                .map(HandEvaluation.GroupStat::getPairStrength)
                .filter(value -> value != null)
                .sorted(Comparator.reverseOrder())
                .toList();
        int pairSuitStrength = groups.stream()
                .filter(group -> group.getPairStrength() != null)
                .mapToInt(HandEvaluation.GroupStat::getSuitStrength)
                .max()
                .orElse(0);

        GameEnums.HandRankType handRankType;
        String summary;
        if (mamaKill) {
            handRankType = GameEnums.HandRankType.MAMA_KILL;
            summary = "Both groups resolve to zero points";
        } else if (reportedFish && fishTypeCode != null
                && GameEnums.FishType.of(fishTypeCode) == GameEnums.FishType.SUPREME
                && intrinsicFish == GameEnums.FishType.SUPREME) {
            handRankType = GameEnums.HandRankType.SUPREME_FISH;
            summary = "Reported supreme fish";
        } else if (reportedFish && fishTypeCode != null
                && GameEnums.FishType.of(fishTypeCode) == GameEnums.FishType.NORMAL
                && intrinsicFish == GameEnums.FishType.NORMAL) {
            handRankType = GameEnums.HandRankType.NORMAL_FISH;
            summary = "Reported normal fish";
        } else if (!pairStrengths.isEmpty()) {
            handRankType = GameEnums.HandRankType.PAIR;
            summary = "Contains at least one pair";
        } else {
            handRankType = GameEnums.HandRankType.POINT;
            summary = "Compare by points";
        }

        return HandEvaluation.builder()
                .handRankType(handRankType)
                .reportedFish(reportedFish)
                .naturalSupremeFish(naturalSupreme)
                .groups(groups)
                .pairStrengths(pairStrengths)
                .pairSuitStrength(pairSuitStrength)
                .summary(summary)
                .build();
    }

    public int compareHands(HandEvaluation challenger, HandEvaluation defender) {
        int typeCompare = Integer.compare(challenger.getHandRankType().getPriority(), defender.getHandRankType().getPriority());
        if (typeCompare != 0) {
            return typeCompare;
        }
        int pairCompare = comparePairStrengths(challenger.getPairStrengths(), defender.getPairStrengths());
        if (pairCompare != 0) {
            return pairCompare;
        }
        int pairSuitCompare = Integer.compare(challenger.getPairSuitStrength(), defender.getPairSuitStrength());
        if (pairSuitCompare != 0) {
            return pairSuitCompare;
        }
        for (int index = 0; index < Math.min(challenger.getGroups().size(), defender.getGroups().size()); index++) {
            int pointCompare = Integer.compare(challenger.getGroups().get(index).getPoint(), defender.getGroups().get(index).getPoint());
            if (pointCompare != 0) {
                return pointCompare;
            }
            int suitCompare = Integer.compare(challenger.getGroups().get(index).getSuitStrength(), defender.getGroups().get(index).getSuitStrength());
            if (suitCompare != 0) {
                return suitCompare;
            }
        }
        return 0;
    }

    public int comparePairStrengths(List<Integer> first, List<Integer> second) {
        int size = Math.max(first.size(), second.size());
        for (int index = 0; index < size; index++) {
            int left = index < first.size() ? first.get(index) : 0;
            int right = index < second.size() ? second.get(index) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    public int baseCompareRate(HandEvaluation idleHand, HandEvaluation bankerHand) {
        boolean highRate = idleHand.getHandRankType() == GameEnums.HandRankType.MAMA_KILL
                || bankerHand.getHandRankType() == GameEnums.HandRankType.MAMA_KILL
                || idleHand.getHandRankType() == GameEnums.HandRankType.SUPREME_FISH
                || bankerHand.getHandRankType() == GameEnums.HandRankType.SUPREME_FISH
                || idleHand.getHandRankType() == GameEnums.HandRankType.NORMAL_FISH
                || bankerHand.getHandRankType() == GameEnums.HandRankType.NORMAL_FISH;
        return highRate ? 4 : 2;
    }

    public GameEnums.AttackType chooseAiAttack(HandEvaluation idleHand, boolean reportedFish) {
        if (reportedFish) {
            return GameEnums.AttackType.STRONG;
        }
        if (idleHand.getHandRankType() == GameEnums.HandRankType.MAMA_KILL) {
            return GameEnums.AttackType.STRONG;
        }
        int roll = random.nextInt(100);
        if (roll < 40) {
            return GameEnums.AttackType.STRONG;
        }
        if (roll < 70) {
            return GameEnums.AttackType.ASK_RUN;
        }
        return GameEnums.AttackType.HIDDEN;
    }

    public GameEnums.BankerResponseType chooseAiResponse(GameEnums.AttackType attackType) {
        return switch (attackType) {
            case ASK_RUN -> random.nextBoolean() ? GameEnums.BankerResponseType.GIVE_WAY : GameEnums.BankerResponseType.CALL_KILL;
            case HIDDEN -> random.nextBoolean() ? GameEnums.BankerResponseType.OPEN : GameEnums.BankerResponseType.NOT_OPEN;
            default -> null;
        };
    }

    public boolean chooseAiAcceptCallKill() {
        return random.nextBoolean();
    }

    private String resolveChangeCard(List<GameCard> group, String changeCard, List<GameCard> handCards) {
        List<GameCard> jokers = group.stream()
                .filter(GameCard::isJoker)
                .toList();
        if (jokers.isEmpty()) {
            return null;
        }

        long totalJokers = handCards.stream().filter(GameCard::isJoker).count();
        String targetRank;
        if (totalJokers == 3) {
            List<GameCard> normalCards = handCards.stream()
                    .filter(card -> !card.isJoker())
                    .toList();
            if (normalCards.size() != 1) {
                throw new BusinessException("Three-joker hands must contain exactly one normal card");
            }
            targetRank = normalCards.get(0).getRankLabel();
        } else {
            if (jokers.size() > 1) {
                throw new BusinessException("Only three-joker hands can place two jokers in one group");
            }
            GameCard pairedCard = group.stream()
                    .filter(card -> !card.isJoker())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("A joker group must include one non-joker card"));
            targetRank = pairedCard.getRankLabel();
        }

        String normalized = normalizeChange(changeCard);
        String expected = buildChangeText(jokers, targetRank);
        if (normalized != null && !expected.equals(normalized)) {
            throw new BusinessException(totalJokers == 3
                    ? "Three-joker hands must copy the only normal card"
                    : "Joker must copy the other card in the same group");
        }
        jokers.forEach(joker -> joker.setAssignedRankLabel(targetRank));
        return expected;
    }

    private String buildChangeText(List<GameCard> jokers, String targetRank) {
        String jokerNames = jokers.stream()
                .map(GameCard::getJokerName)
                .sorted()
                .collect(Collectors.joining(","));
        return jokerNames + ":" + targetRank;
    }

    private HandEvaluation.GroupStat buildGroupStat(List<GameCard> cards) {
        int point = cards.stream().mapToInt(GameCard::getEffectivePointValue).sum() % 10;
        Integer pairStrength = cards.get(0).getEffectiveRankLabel().equals(cards.get(1).getEffectiveRankLabel())
                ? GameCard.rankStrength(cards.get(0).getEffectiveRankLabel())
                : null;
        int suitStrength = cards.stream().mapToInt(GameCard::getSuitStrength).max().orElse(0);
        return HandEvaluation.GroupStat.builder()
                .cards(cards)
                .point(point)
                .pairStrength(pairStrength)
                .suitStrength(suitStrength)
                .build();
    }

    private GameCard parseCard(String text) {
        if (text.equals(BIG_JOKER) || text.equals(SMALL_JOKER) || text.equals(WHITE_CARD)) {
            return joker(text);
        }
        for (String suit : SUITS) {
            if (text.startsWith(suit)) {
                return normalCard(suit, normalizeRankLabel(text.substring(suit.length())));
            }
        }
        throw new BusinessException("Unknown card: " + text);
    }

    private GameCard normalCard(String suit, String rank) {
        return GameCard.builder()
                .cardId(suit + rank)
                .suit(suit)
                .rankLabel(rank)
                .rankValue(GameCard.rankStrength(rank))
                .pointValue(GameCard.pointByRank(rank))
                .joker(false)
                .jokerName("")
                .build();
    }

    private GameCard joker(String name) {
        return GameCard.builder()
                .cardId(name)
                .suit(NONE)
                .rankLabel(name)
                .rankValue(0)
                .pointValue(0)
                .joker(true)
                .jokerName(name)
                .build();
    }

    private String normalizeChange(String changeCard) {
        if (changeCard == null || changeCard.isBlank()) {
            return null;
        }
        String[] parts = changeCard.split(":");
        if (parts.length != 2) {
            throw new BusinessException("Invalid joker assignment format");
        }
        String jokerNames = Arrays.stream(parts[0].split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
        if (jokerNames.isEmpty()) {
            throw new BusinessException("Invalid joker assignment format");
        }
        return jokerNames + ":" + normalizeRankLabel(parts[1].trim());
    }

    private String normalizeRankLabel(String value) {
        String normalized = value.replace(SPADE, "")
                .replace(HEART, "")
                .replace(CLUB, "")
                .replace(DIAMOND, "")
                .trim();
        if (!RANKS.contains(normalized)) {
            throw new BusinessException("Unsupported rank: " + value);
        }
        return normalized;
    }
}
