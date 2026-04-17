package com.shuiyu.game.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuiyu.game.common.BusinessException;
import com.shuiyu.game.dto.RequestModels;
import com.shuiyu.game.dto.ResponseModels;
import com.shuiyu.game.entity.GameGlobalSettle;
import com.shuiyu.game.entity.GameGroupFish;
import com.shuiyu.game.entity.GameHandCard;
import com.shuiyu.game.entity.GameMatch;
import com.shuiyu.game.entity.GameRoom;
import com.shuiyu.game.entity.GameRoomUser;
import com.shuiyu.game.entity.GameRoundSettle;
import com.shuiyu.game.entity.SysUser;
import com.shuiyu.game.mapper.GameGlobalSettleMapper;
import com.shuiyu.game.mapper.GameGroupFishMapper;
import com.shuiyu.game.mapper.GameHandCardMapper;
import com.shuiyu.game.mapper.GameMatchMapper;
import com.shuiyu.game.mapper.GameRoomMapper;
import com.shuiyu.game.mapper.GameRoomUserMapper;
import com.shuiyu.game.mapper.GameRoundSettleMapper;
import com.shuiyu.game.mapper.SysUserMapper;
import com.shuiyu.game.model.GameCard;
import com.shuiyu.game.model.GameEnums;
import com.shuiyu.game.model.GroupedHand;
import com.shuiyu.game.model.HandEvaluation;
import com.shuiyu.game.model.PendingAttackContext;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GameMatchMapper gameMatchMapper;
    private final GameHandCardMapper gameHandCardMapper;
    private final GameGroupFishMapper gameGroupFishMapper;
    private final GameRoundSettleMapper gameRoundSettleMapper;
    private final GameGlobalSettleMapper gameGlobalSettleMapper;
    private final GameRoomMapper gameRoomMapper;
    private final GameRoomUserMapper gameRoomUserMapper;
    private final SysUserMapper sysUserMapper;
    private final RoomService roomService;
    private final GameRuleEngine gameRuleEngine;
    private final GameWebSocketHub webSocketHub;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingAttackContext>> pendingAttackMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Long>> nextRoundReadyMap = new ConcurrentHashMap<>();

    @Transactional
    public ResponseModels.GameStateView deal(Long roomId, Long loginUserId) {
        GameRoom room = roomService.getRoom(roomId);
        if (!room.getHouseOwnerId().equals(loginUserId)) {
            throw new BusinessException("Only the room owner can deal cards");
        }
        GameMatch latestMatch = getLatestMatch(roomId);
        if (latestMatch != null && latestMatch.getMatchStatus() != GameEnums.MatchStatus.FINISHED.getCode()) {
            throw new BusinessException("There is already an active match in this room");
        }

        List<GameRoomUser> roomUsers = roomService.getRoomUsers(roomId);
        List<GameRoomUser> idleUsers = roomUsers.stream()
                .filter(item -> item.getUserRole().equals(GameEnums.RoomRole.IDLE.getCode()))
                .sorted(Comparator.comparing(GameRoomUser::getIdleSort))
                .toList();
        if (idleUsers.isEmpty()) {
            throw new BusinessException("At least one idle player is required");
        }
        if (room.getBankerType().equals(GameEnums.BankerType.AI.getCode())
                && room.getCurrentIdle() < room.getMaxIdle()) {
            throw new BusinessException("AI banker mode requires all idle seats to be filled before starting");
        }
        nextRoundReadyMap.remove(roomId);

        GameMatch match = new GameMatch();
        match.setRoomId(roomId);
        match.setBankerUserId(room.getBankerUserId());
        match.setMatchStatus(GameEnums.MatchStatus.GROUPING.getCode());
        match.setCurrentIdleSort(idleUsers.get(0).getIdleSort());
        match.setBankerGroupLocked(0);
        match.setStartTime(LocalDateTime.now());
        gameMatchMapper.insert(match);

        List<GameCard> deck = gameRuleEngine.buildDeck();
        List<GameRoomUser> orderedPlayers = roomUsers.stream()
                .sorted(Comparator.comparing(GameRoomUser::getUserRole).thenComparing(item -> item.getIdleSort() == null ? 0 : item.getIdleSort()))
                .toList();
        int pointer = 0;
        for (GameRoomUser player : orderedPlayers) {
            GameHandCard handCard = new GameHandCard();
            handCard.setMatchId(match.getMatchId());
            handCard.setUserId(player.getUserId());
            handCard.setHandCards(gameRuleEngine.serializeCards(deck.subList(pointer, pointer + 4)));
            gameHandCardMapper.insert(handCard);
            pointer += 4;
        }

        gameRoomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                .eq(GameRoom::getRoomId, roomId)
                .set(GameRoom::getRoomStatus, GameEnums.RoomStatus.PLAYING.getCode()));

        autoGroupAiPlayers(match, roomUsers);
        pushEvent(roomId, "deal_completed");
        autoAdvanceAiTurn(match.getMatchId());
        return getState(roomId, loginUserId);
    }

    @Transactional
    public ResponseModels.GameStateView readyNextRound(RequestModels.ReadyNextRoundRequest request, Long loginUserId) {
        assertLoginUser(request.userId(), loginUserId);
        GameRoom room = roomService.getRoom(request.roomId());
        GameMatch latestMatch = getLatestMatch(request.roomId());
        if (latestMatch == null || !latestMatch.getMatchStatus().equals(GameEnums.MatchStatus.FINISHED.getCode())) {
            throw new BusinessException("Current match is not finished yet");
        }

        List<GameRoomUser> roomUsers = roomService.getRoomUsers(request.roomId());
        boolean joinedRoom = roomUsers.stream().anyMatch(item -> item.getUserId().equals(request.userId()));
        if (!joinedRoom) {
            throw new BusinessException("You are not in this room");
        }

        Set<Long> readyUsers = getOrCreateReadyUsers(request.roomId(), roomUsers);
        readyUsers.add(request.userId());
        pushEvent(request.roomId(), "ready_next_round");
        if (readyUsers.size() >= roomUsers.size()) {
            return deal(request.roomId(), room.getHouseOwnerId());
        }
        return getState(request.roomId(), loginUserId);
    }

    @Transactional
    public void tryAutoStartAiRoom(Long roomId) {
        GameRoom room = roomService.getRoom(roomId);
        if (!room.getBankerType().equals(GameEnums.BankerType.AI.getCode())) {
            return;
        }
        if (room.getCurrentIdle() < room.getMaxIdle()) {
            return;
        }
        GameMatch latestMatch = getLatestMatch(roomId);
        if (latestMatch != null && !latestMatch.getMatchStatus().equals(GameEnums.MatchStatus.FINISHED.getCode())) {
            return;
        }
        deal(roomId, room.getHouseOwnerId());
    }

    @Transactional
    public ResponseModels.GameStateView submitGroup(RequestModels.GroupRequest request, Long loginUserId) {
        assertLoginUser(request.userId(), loginUserId);
        GameMatch match = getActiveMatch(request.roomId());
        GameRoom room = roomService.getRoom(request.roomId());
        boolean bankerGrouping = room.getBankerUserId().equals(request.userId());
        if (bankerGrouping && match.getBankerGroupLocked() == 1) {
            throw new BusinessException("Banker grouping is locked");
        }

        GameHandCard handCard = getHandCard(match.getMatchId(), request.userId());
        GroupedHand groupedHand = gameRuleEngine.validateAndBuildGrouping(
                gameRuleEngine.parseCards(handCard.getHandCards()),
                request.group1(),
                request.group2(),
                request.changeCard1(),
                request.changeCard2()
        );
        upsertGroupFish(match.getMatchId(), request.userId(), groupedHand, false, null);
        handCard.setChangeCard1(groupedHand.getChangeCard1());
        handCard.setChangeCard2(groupedHand.getChangeCard2());
        gameHandCardMapper.updateById(handCard);
        if (bankerGrouping && match.getBankerGroupLocked() == 0) {
            match.setBankerGroupLocked(1);
            gameMatchMapper.updateById(match);
        }
        pushEvent(request.roomId(), "group_updated");
        return getState(request.roomId(), loginUserId);
    }

    @Transactional
    public ResponseModels.GameStateView reportFish(RequestModels.ReportFishRequest request, Long loginUserId) {
        assertLoginUser(request.userId(), loginUserId);
        GameMatch match = getActiveMatch(request.roomId());
        GroupedHand groupedHand = loadGroupedHand(match.getMatchId(), request.userId());
        GameEnums.FishType intrinsicFish = gameRuleEngine.detectFishType(groupedHand);
        GameEnums.FishType requestedFish = GameEnums.FishType.of(request.fishType());
        if (intrinsicFish == null) {
            throw new BusinessException("Current hand is not a fish hand");
        }
        if (intrinsicFish != requestedFish) {
            throw new BusinessException("Reported fish type does not match the hand");
        }
        upsertGroupFish(match.getMatchId(), request.userId(), groupedHand, true, requestedFish);
        pushEvent(request.roomId(), "fish_reported");
        return getState(request.roomId(), loginUserId);
    }

    @Transactional
    public ResponseModels.GameStateView attack(RequestModels.AttackRequest request, Long loginUserId) {
        assertLoginUser(request.userId(), loginUserId);
        GameMatch match = getActiveMatch(request.roomId());
        ensureIdleHasNoPending(match.getMatchId(), request.userId());
        GameRoomUser currentIdle = getIdleUser(request.roomId(), request.userId());
        if (hasIdleSettled(match.getMatchId(), request.userId())) {
            throw new BusinessException("This idle seat has already finished its attack");
        }

        BattleContext battleContext = buildBattleContext(request.roomId(), match, currentIdle);
        if (battleContext.idleGroupedHand() == null || battleContext.idleGroupFish() == null) {
            throw new BusinessException("Idle players must finish grouping before attacking");
        }
        boolean reportedFish = battleContext.idleGroupFish().getIsReportFish() == 1;
        GameEnums.AttackType requestedAttackType = GameEnums.AttackType.of(request.attackType());
        if (requestedAttackType == GameEnums.AttackType.FISH && !reportedFish) {
            throw new BusinessException("Fish attack requires a prior fish report");
        }
        if (reportedFish
                && requestedAttackType != GameEnums.AttackType.STRONG
                && requestedAttackType != GameEnums.AttackType.FISH) {
            throw new BusinessException("Reported fish hands must use strong attack");
        }
        GameEnums.AttackType attackType = reportedFish && requestedAttackType == GameEnums.AttackType.FISH
                ? GameEnums.AttackType.STRONG
                : requestedAttackType;

        GameRoom room = roomService.getRoom(request.roomId());
        SysUser banker = sysUserMapper.selectById(room.getBankerUserId());
        if (attackType == GameEnums.AttackType.STRONG) {
            if (!isBankerReadyForComparison(battleContext) && banker != null
                    && banker.getUserType().equals(GameEnums.UserType.REAL.getCode())) {
                createPendingAction(battleContext, attackType, GameEnums.PendingStage.WAIT_BANKER_GROUP, null, "pending_banker_group");
                return getState(request.roomId(), loginUserId);
            }
            settleByComparison(battleContext, GameEnums.AttackType.STRONG, null);
            return getState(request.roomId(), loginUserId);
        }
        if (attackType == GameEnums.AttackType.FISH) {
            if (!isBankerReadyForComparison(battleContext) && banker != null
                    && banker.getUserType().equals(GameEnums.UserType.REAL.getCode())) {
                createPendingAction(battleContext, attackType, GameEnums.PendingStage.WAIT_BANKER_GROUP, null, "pending_banker_group");
                return getState(request.roomId(), loginUserId);
            }
            settleFishAttack(battleContext);
            return getState(request.roomId(), loginUserId);
        }

        if (banker.getUserType().equals(GameEnums.UserType.AI.getCode())) {
            handleBankerResponse(battleContext, attackType, gameRuleEngine.chooseAiResponse(attackType), request.acceptCallKill());
            return getState(request.roomId(), loginUserId);
        }
        if (request.bankerResponse() != null) {
            handleBankerResponse(battleContext, attackType, GameEnums.BankerResponseType.of(request.bankerResponse()), request.acceptCallKill());
            return getState(request.roomId(), loginUserId);
        }

        createPendingAction(battleContext, attackType, GameEnums.PendingStage.WAIT_BANKER_RESPONSE, null, "pending_banker_response");
        return getState(request.roomId(), loginUserId);
    }

    @Transactional
    public ResponseModels.GameStateView bankerRespond(RequestModels.BankerResponseRequest request, Long loginUserId) {
        assertLoginUser(request.userId(), loginUserId);
        GameMatch match = getActiveMatch(request.roomId());
        PendingAttackContext pending = getPending(match.getMatchId(), request.idleUserId(), GameEnums.PendingStage.WAIT_BANKER_RESPONSE);
        if (!pending.getBankerUserId().equals(request.userId())) {
            throw new BusinessException("Pending banker action does not belong to this user");
        }
        handleBankerResponse(
                buildBattleContext(request.roomId(), match, getIdleUser(request.roomId(), pending.getIdleUserId())),
                pending.getAttackType(),
                GameEnums.BankerResponseType.of(request.bankerResponse()),
                null
        );
        return getState(request.roomId(), loginUserId);
    }

    @Transactional
    public ResponseModels.GameStateView idleDecide(RequestModels.IdleDecisionRequest request, Long loginUserId) {
        assertLoginUser(request.userId(), loginUserId);
        GameMatch match = getActiveMatch(request.roomId());
        PendingAttackContext pending = getPending(match.getMatchId(), request.userId(), GameEnums.PendingStage.WAIT_IDLE_DECISION);
        if (!pending.getIdleUserId().equals(request.userId())) {
            throw new BusinessException("Pending idle decision does not belong to this user");
        }
        finishCallKillDecision(
                buildBattleContext(request.roomId(), match, getIdleUser(request.roomId(), pending.getIdleUserId())),
                request.acceptCallKill()
        );
        return getState(request.roomId(), loginUserId);
    }

    @Transactional
    public ResponseModels.GameStateView processPending(RequestModels.ProcessPendingRequest request, Long loginUserId) {
        assertLoginUser(request.userId(), loginUserId);
        GameMatch match = getActiveMatch(request.roomId());
        PendingAttackContext pending = getPending(match.getMatchId(), request.idleUserId(), GameEnums.PendingStage.WAIT_BANKER_GROUP);
        if (!pending.getBankerUserId().equals(request.userId())) {
            throw new BusinessException("Pending banker action does not belong to this user");
        }
        BattleContext battleContext = buildBattleContext(
                request.roomId(),
                match,
                getIdleUser(request.roomId(), pending.getIdleUserId())
        );
        requireBankerReadyForComparison(battleContext, "Please finish banker grouping before comparing cards");
        removePending(match.getMatchId(), pending.getIdleUserId());
        if (pending.getAttackType() == GameEnums.AttackType.STRONG) {
            settleByComparison(battleContext, GameEnums.AttackType.STRONG, null);
            return getState(request.roomId(), loginUserId);
        }
        if (pending.getAttackType() == GameEnums.AttackType.ASK_RUN
                && pending.getBankerResponseType() == GameEnums.BankerResponseType.CALL_KILL) {
            settleByComparison(battleContext, GameEnums.AttackType.ASK_RUN, GameEnums.BankerResponseType.CALL_KILL);
            return getState(request.roomId(), loginUserId);
        }
        if (pending.getAttackType() == GameEnums.AttackType.HIDDEN
                && pending.getBankerResponseType() == GameEnums.BankerResponseType.OPEN) {
            settleByComparison(battleContext, GameEnums.AttackType.HIDDEN, GameEnums.BankerResponseType.OPEN);
            return getState(request.roomId(), loginUserId);
        }
        throw new BusinessException("Unsupported pending action");
    }

    public ResponseModels.GameStateView getState(Long roomId, Long viewerUserId) {
        return buildState(roomId, getLatestMatch(roomId), viewerUserId);
    }

    public ResponseModels.GameStateView getStateByMatch(Long matchId, Long viewerUserId) {
        GameMatch match = gameMatchMapper.selectById(matchId);
        if (match == null) {
            throw new BusinessException("Match does not exist");
        }
        return buildState(match.getRoomId(), match, viewerUserId);
    }

    @Transactional
    public void autoAdvanceAiTurn(Long matchId) {
        GameMatch match = gameMatchMapper.selectById(matchId);
        if (match == null || match.getMatchStatus().equals(GameEnums.MatchStatus.FINISHED.getCode())) {
            return;
        }
        List<GameRoomUser> unresolvedIdleUsers = getUnsettledIdleUsers(match.getRoomId(), match.getMatchId()).stream()
                .filter(item -> !hasPendingForIdle(match.getMatchId(), item.getUserId()))
                .sorted(Comparator.comparing(GameRoomUser::getIdleSort))
                .toList();
        GameRoomUser currentIdle = unresolvedIdleUsers.stream()
                .filter(item -> {
                    SysUser idleUser = sysUserMapper.selectById(item.getUserId());
                    return idleUser != null && idleUser.getUserType().equals(GameEnums.UserType.AI.getCode());
                })
                .findFirst()
                .orElse(null);
        if (currentIdle == null) {
            refreshCurrentIdleSort(match);
            return;
        }

        BattleContext battleContext = buildBattleContext(match.getRoomId(), match, currentIdle);
        GameEnums.AttackType attackType = gameRuleEngine.chooseAiAttack(
                battleContext.idleEvaluation(),
                battleContext.idleGroupFish().getIsReportFish() == 1
        );
        GameRoom room = roomService.getRoom(match.getRoomId());
        SysUser banker = sysUserMapper.selectById(room.getBankerUserId());
        if (attackType == GameEnums.AttackType.STRONG) {
            if (!isBankerReadyForComparison(battleContext) && banker != null
                    && banker.getUserType().equals(GameEnums.UserType.REAL.getCode())) {
                createPendingAction(battleContext, attackType, GameEnums.PendingStage.WAIT_BANKER_GROUP, null, "pending_banker_group");
                autoAdvanceAiTurn(matchId);
                return;
            }
            settleByComparison(battleContext, attackType, null);
            return;
        }
        if (banker.getUserType().equals(GameEnums.UserType.AI.getCode())) {
            handleBankerResponse(battleContext, attackType, gameRuleEngine.chooseAiResponse(attackType), gameRuleEngine.chooseAiAcceptCallKill());
            return;
        }

        createPendingAction(battleContext, attackType, GameEnums.PendingStage.WAIT_BANKER_RESPONSE, null, "pending_banker_response");
        autoAdvanceAiTurn(matchId);
    }

    private void handleBankerResponse(BattleContext battleContext,
                                      GameEnums.AttackType attackType,
                                      GameEnums.BankerResponseType responseType,
                                      @Nullable Boolean acceptCallKill) {
        if (attackType == GameEnums.AttackType.HIDDEN) {
            if (responseType == GameEnums.BankerResponseType.NOT_OPEN) {
                int rate = battleContext.idleEvaluation().getHandRankType() == GameEnums.HandRankType.MAMA_KILL
                        || battleContext.idleEvaluation().getHandRankType() == GameEnums.HandRankType.NORMAL_FISH
                        || battleContext.idleEvaluation().getHandRankType() == GameEnums.HandRankType.SUPREME_FISH ? 4 : 1;
                settleRound(battleContext, attackType, responseType,
                        GameEnums.RoundResultType.IDLE_WIN, rate, battleContext.currentIdle().getUserId());
                return;
            }
            requireBankerReadyForComparison(battleContext, "Please finish banker grouping before opening cards");
            settleByComparison(battleContext, attackType, responseType);
            return;
        }

        if (responseType == GameEnums.BankerResponseType.GIVE_WAY) {
            settleRound(battleContext, attackType, responseType, GameEnums.RoundResultType.DRAW, 1, null);
            return;
        }

        SysUser idleUser = sysUserMapper.selectById(battleContext.currentIdle().getUserId());
        if (idleUser != null && idleUser.getUserType().equals(GameEnums.UserType.AI.getCode())) {
            finishCallKillDecision(battleContext, gameRuleEngine.chooseAiAcceptCallKill());
            return;
        }

        if (acceptCallKill == null) {
            putPending(PendingAttackContext.builder()
                    .roomId(battleContext.room().getRoomId())
                    .matchId(battleContext.match().getMatchId())
                    .bankerUserId(battleContext.room().getBankerUserId())
                    .idleUserId(battleContext.currentIdle().getUserId())
                    .idleSort(battleContext.currentIdle().getIdleSort())
                    .attackType(attackType)
                    .stage(GameEnums.PendingStage.WAIT_IDLE_DECISION)
                    .bankerResponseType(responseType)
                    .createdAt(LocalDateTime.now())
                    .build());
            refreshCurrentIdleSort(battleContext.match());
            pushEvent(battleContext.room().getRoomId(), "pending_idle_decision");
            return;
        }
        finishCallKillDecision(battleContext, acceptCallKill);
    }

    private void finishCallKillDecision(BattleContext battleContext, Boolean acceptCallKill) {
        removePending(battleContext.match().getMatchId(), battleContext.currentIdle().getUserId());
        if (Boolean.TRUE.equals(acceptCallKill)) {
            settleRound(battleContext, GameEnums.AttackType.ASK_RUN, GameEnums.BankerResponseType.CALL_KILL,
                    GameEnums.RoundResultType.BANKER_WIN, 1, battleContext.currentIdle().getUserId());
            return;
        }
        if (!isBankerReadyForComparison(battleContext)) {
            createPendingAction(battleContext, GameEnums.AttackType.ASK_RUN,
                    GameEnums.PendingStage.WAIT_BANKER_GROUP,
                    GameEnums.BankerResponseType.CALL_KILL,
                    "pending_banker_group");
            return;
        }
        settleByComparison(battleContext, GameEnums.AttackType.ASK_RUN, GameEnums.BankerResponseType.CALL_KILL);
    }

    private void settleFishAttack(BattleContext battleContext) {
        requireBankerReadyForComparison(battleContext, "Please finish banker grouping before comparing fish hands");
        GameEnums.FishType bankerIntrinsicFish = gameRuleEngine.detectFishType(battleContext.bankerGroupedHand());
        if (battleContext.bankerEvaluation().getHandRankType() == GameEnums.HandRankType.MAMA_KILL) {
            settleRound(battleContext, GameEnums.AttackType.FISH, null,
                    GameEnums.RoundResultType.BANKER_WIN, 4, battleContext.currentIdle().getUserId());
            return;
        }
        if (bankerIntrinsicFish == null) {
            settleRound(battleContext, GameEnums.AttackType.FISH, null,
                    GameEnums.RoundResultType.IDLE_WIN, 4, battleContext.room().getBankerUserId());
            return;
        }
        settleByComparison(battleContext, GameEnums.AttackType.FISH, null, 4);
    }

    private void settleByComparison(BattleContext battleContext,
                                    GameEnums.AttackType attackType,
                                    @Nullable GameEnums.BankerResponseType responseType) {
        requireBankerReadyForComparison(battleContext, "Please finish banker grouping before comparing cards");
        settleByComparison(battleContext, attackType, responseType,
                gameRuleEngine.baseCompareRate(battleContext.idleEvaluation(), battleContext.bankerEvaluation()));
    }

    private void settleByComparison(BattleContext battleContext,
                                    GameEnums.AttackType attackType,
                                    @Nullable GameEnums.BankerResponseType responseType,
                                    int rate) {
        requireBankerReadyForComparison(battleContext, "Please finish banker grouping before comparing cards");
        removePending(battleContext.match().getMatchId(), battleContext.currentIdle().getUserId());
        int comparison = gameRuleEngine.compareHands(battleContext.idleEvaluation(), battleContext.bankerEvaluation());
        if (comparison > 0) {
            settleRound(battleContext, attackType, responseType,
                    GameEnums.RoundResultType.IDLE_WIN, rate, battleContext.room().getBankerUserId());
        } else if (comparison < 0) {
            settleRound(battleContext, attackType, responseType,
                    GameEnums.RoundResultType.BANKER_WIN, rate, battleContext.currentIdle().getUserId());
        } else {
            settleRound(battleContext, attackType, responseType, GameEnums.RoundResultType.DRAW, rate, null);
        }
    }

    private void settleRound(BattleContext battleContext,
                             GameEnums.AttackType attackType,
                             @Nullable GameEnums.BankerResponseType responseType,
                             GameEnums.RoundResultType resultType,
                             int rate,
                             Long drinkUserId) {
        removePending(battleContext.match().getMatchId(), battleContext.currentIdle().getUserId());
        int effectiveRate = resultType == GameEnums.RoundResultType.DRAW ? 0 : rate;
        GameRoundSettle settle = new GameRoundSettle();
        settle.setMatchId(battleContext.match().getMatchId());
        settle.setBankerUserId(battleContext.room().getBankerUserId());
        settle.setIdleUserId(battleContext.currentIdle().getUserId());
        settle.setAttackType(attackType.getCode());
        settle.setBankerResponse(responseType == null ? null : responseType.getCode());
        settle.setResult(resultType.getCode());
        settle.setRate(effectiveRate);
        settle.setDrinkUserId(resultType == GameEnums.RoundResultType.DRAW ? null : drinkUserId);
        settle.setSettleTime(LocalDateTime.now());
        gameRoundSettleMapper.insert(settle);

        Integer nextIdleSort = findNextUnsettledIdleSort(battleContext.room().getRoomId(), battleContext.match().getMatchId());

        if (nextIdleSort == null) {
            battleContext.match().setMatchStatus(GameEnums.MatchStatus.FINISHED.getCode());
            battleContext.match().setEndTime(LocalDateTime.now());
            gameMatchMapper.updateById(battleContext.match());
            getOrCreateReadyUsers(battleContext.room().getRoomId(), roomService.getRoomUsers(battleContext.room().getRoomId()));
            buildGlobalSettle(battleContext.match());
            gameRoomMapper.update(null, new LambdaUpdateWrapper<GameRoom>()
                    .eq(GameRoom::getRoomId, battleContext.room().getRoomId())
                    .set(GameRoom::getRoomStatus, GameEnums.RoomStatus.FINISHED.getCode()));
            pushEvent(battleContext.room().getRoomId(), "global_settle");
        } else {
            battleContext.match().setCurrentIdleSort(findNextAttackableIdleSort(battleContext.room().getRoomId(), battleContext.match().getMatchId()));
            battleContext.match().setMatchStatus(GameEnums.MatchStatus.COMPARING.getCode());
            gameMatchMapper.updateById(battleContext.match());
            pushEvent(battleContext.room().getRoomId(), "round_settle");
            autoAdvanceAiTurn(battleContext.match().getMatchId());
        }
    }

    private void buildGlobalSettle(GameMatch match) {
        List<GameRoundSettle> settles = getRoundSettles(match.getMatchId());
        int bankerWin = (int) settles.stream().filter(item -> item.getResult().equals(GameEnums.RoundResultType.BANKER_WIN.getCode())).count();
        int idleWin = (int) settles.stream().filter(item -> item.getResult().equals(GameEnums.RoundResultType.IDLE_WIN.getCode())).count();
        int drawCount = (int) settles.stream().filter(item -> item.getResult().equals(GameEnums.RoundResultType.DRAW.getCode())).count();
        int maxRate = settles.stream().mapToInt(GameRoundSettle::getRate).max().orElse(1);

        GameGlobalSettle global = new GameGlobalSettle();
        global.setMatchId(match.getMatchId());
        global.setBankerWinCount(bankerWin);
        global.setIdleWinCount(idleWin);
        global.setDrawCount(drawCount);
        global.setMaxRate(maxRate);
        global.setSettleDetail(toJson(buildState(match.getRoomId(), match, match.getBankerUserId()).roundSettles()));
        global.setSettleTime(LocalDateTime.now());
        gameGlobalSettleMapper.insert(global);
    }

    private void autoGroupAiPlayers(GameMatch match, List<GameRoomUser> roomUsers) {
        Map<Long, SysUser> userMap = roomService.loadUsers(roomUsers);
        for (GameRoomUser roomUser : roomUsers) {
            SysUser user = userMap.get(roomUser.getUserId());
            if (user == null || !user.getUserType().equals(GameEnums.UserType.AI.getCode())) {
                continue;
            }
            GameHandCard handCard = getHandCard(match.getMatchId(), roomUser.getUserId());
            GroupedHand groupedHand = gameRuleEngine.chooseBestGrouping(gameRuleEngine.parseCards(handCard.getHandCards()));
            GameEnums.FishType fishType = gameRuleEngine.detectFishType(groupedHand);
            upsertGroupFish(match.getMatchId(), roomUser.getUserId(), groupedHand, fishType != null, fishType);
            handCard.setChangeCard1(groupedHand.getChangeCard1());
            handCard.setChangeCard2(groupedHand.getChangeCard2());
            gameHandCardMapper.updateById(handCard);
        }
    }

    private ResponseModels.GameStateView buildState(Long roomId, @Nullable GameMatch match, Long viewerUserId) {
        ResponseModels.RoomView roomView = roomService.getRoomView(roomId, match == null ? null : match.getCurrentIdleSort());
        List<GameRoomUser> roomUsers = roomService.getRoomUsers(roomId);
        Map<Long, SysUser> userMap = roomService.loadUsers(roomUsers);
        if (match == null) {
            List<ResponseModels.PlayerCardsView> waitingPlayers = roomUsers.stream()
                    .sorted(Comparator.comparing(GameRoomUser::getUserRole)
                            .thenComparing(item -> item.getIdleSort() == null ? 0 : item.getIdleSort()))
                    .map(item -> new ResponseModels.PlayerCardsView(
                            item.getUserId(),
                            userMap.get(item.getUserId()) == null ? "" : userMap.get(item.getUserId()).getNickname(),
                            item.getUserRole(),
                            item.getIdleSort(),
                            false,
                            false,
                            false,
                            false,
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            null,
                            null
                    ))
                    .toList();
            return new ResponseModels.GameStateView(roomView, null, null, null, false, waitingPlayers, List.of(), null, List.of(), null);
        }

        Map<Long, GameHandCard> handMap = getHandCards(match.getMatchId());
        Map<Long, GameGroupFish> groupMap = getGroupFishMap(match.getMatchId());
        List<GameRoundSettle> roundSettles = getRoundSettles(match.getMatchId());
        GameGlobalSettle globalSettle = gameGlobalSettleMapper.selectOne(new LambdaQueryWrapper<GameGlobalSettle>()
                .eq(GameGlobalSettle::getMatchId, match.getMatchId()));
        List<PendingAttackContext> pendingActions = getPendingActions(match.getMatchId());
        Set<Long> reportedFishUsers = groupMap.values().stream()
                .filter(item -> item.getIsReportFish() == 1)
                .map(GameGroupFish::getUserId)
                .collect(Collectors.toSet());
        Set<Long> readyUsers = match.getMatchStatus().equals(GameEnums.MatchStatus.FINISHED.getCode())
                ? getOrCreateReadyUsers(roomId, roomUsers)
                : Set.of();

        GameRoundSettle latestRoundSettle = roundSettles.isEmpty() ? null : roundSettles.get(roundSettles.size() - 1);
        Set<Long> visibleUsers = resolveVisibleUsers(
                roomUsers,
                match,
                viewerUserId,
                reportedFishUsers,
                pendingActions,
                latestRoundSettle,
                roomView.bankerUserId()
        );
        List<ResponseModels.PlayerCardsView> players = roomUsers.stream()
                .sorted(Comparator.comparing(GameRoomUser::getUserRole).thenComparing(item -> item.getIdleSort() == null ? 0 : item.getIdleSort()))
                .map(item -> buildPlayerCardsView(item, userMap.get(item.getUserId()), handMap.get(item.getUserId()), groupMap.get(item.getUserId()), visibleUsers, readyUsers))
                .toList();
        List<ResponseModels.RoundSettleView> settleViews = filterVisibleRoundSettles(roundSettles, match, viewerUserId, roomView.bankerUserId()).stream()
                .map(item -> {
                    SysUser idleUser = userMap.get(item.getIdleUserId());
                    Integer idleSort = roomUsers.stream()
                            .filter(roomUser -> roomUser.getUserId().equals(item.getIdleUserId()))
                            .map(GameRoomUser::getIdleSort)
                            .findFirst()
                            .orElse(null);
                    return new ResponseModels.RoundSettleView(
                            item.getIdleUserId(),
                            idleUser == null ? "" : idleUser.getNickname(),
                            idleSort,
                            item.getAttackType(),
                            item.getBankerResponse(),
                            item.getResult(),
                            item.getRate(),
                            item.getDrinkUserId(),
                            formatTime(item.getSettleTime())
                    );
                })
                .toList();
        ResponseModels.GlobalSettleView globalView = globalSettle == null ? null : new ResponseModels.GlobalSettleView(
                globalSettle.getBankerWinCount(),
                globalSettle.getIdleWinCount(),
                globalSettle.getDrawCount(),
                globalSettle.getMaxRate(),
                formatTime(globalSettle.getSettleTime()),
                globalSettle.getSettleDetail()
        );
        List<ResponseModels.PendingActionView> pendingViews = pendingActions.stream()
                .map(item -> new ResponseModels.PendingActionView(
                        item.getStage().name(),
                        item.getAttackType().name(),
                        item.getBankerUserId(),
                        item.getIdleUserId(),
                        userMap.get(item.getIdleUserId()) == null ? "" : userMap.get(item.getIdleUserId()).getNickname(),
                        item.getIdleSort(),
                        item.getStage().getLabel()
                ))
                .toList();
        ResponseModels.PendingActionView pendingView = pendingViews.stream()
                .filter(item -> item.idleUserId().equals(viewerUserId) || item.bankerUserId().equals(viewerUserId))
                .findFirst()
                .orElse(null);
        return new ResponseModels.GameStateView(
                roomView,
                match.getMatchId(),
                match.getMatchStatus(),
                match.getCurrentIdleSort(),
                match.getBankerGroupLocked() == 1,
                players,
                settleViews,
                globalView,
                pendingViews,
                pendingView
        );
    }

    private ResponseModels.PlayerCardsView buildPlayerCardsView(GameRoomUser roomUser,
                                                                SysUser user,
                                                                @Nullable GameHandCard handCard,
                                                                @Nullable GameGroupFish groupFish,
                                                                Set<Long> visibleUsers,
                                                                Set<Long> readyUsers) {
        boolean faceDown = handCard != null && !visibleUsers.contains(roomUser.getUserId());
        return new ResponseModels.PlayerCardsView(
                roomUser.getUserId(),
                user == null ? "" : user.getNickname(),
                roomUser.getUserRole(),
                roomUser.getIdleSort(),
                faceDown,
                groupFish != null,
                groupFish != null && groupFish.getIsReportFish() == 1 && !faceDown,
                readyUsers.contains(roomUser.getUserId()),
                groupFish != null && !faceDown ? groupFish.getFishType() : null,
                handCard == null || faceDown ? List.of() : splitCardText(handCard.getHandCards()),
                groupFish == null || faceDown ? List.of() : splitCardText(groupFish.getGroup1()),
                groupFish == null || faceDown ? List.of() : splitCardText(groupFish.getGroup2()),
                handCard != null && !faceDown ? handCard.getChangeCard1() : null,
                handCard != null && !faceDown ? handCard.getChangeCard2() : null
        );
    }

    private Set<Long> resolveVisibleUsers(List<GameRoomUser> roomUsers,
                                          GameMatch match,
                                          Long viewerUserId,
                                          Set<Long> reportedFishUsers,
                                          List<PendingAttackContext> pendingActions,
                                          @Nullable GameRoundSettle latestRoundSettle,
                                          Long bankerUserId) {
        if (match.getMatchStatus().equals(GameEnums.MatchStatus.FINISHED.getCode())) {
            return roomUsers.stream().map(GameRoomUser::getUserId).collect(Collectors.toSet());
        }
        Set<Long> visible = new java.util.HashSet<>();
        visible.add(viewerUserId);
        visible.addAll(reportedFishUsers);
        if (pendingActions.isEmpty()) {
            if (latestRoundSettle != null && shouldRevealRoundCards(latestRoundSettle)) {
                if (viewerUserId.equals(latestRoundSettle.getIdleUserId()) || viewerUserId.equals(bankerUserId)) {
                    visible.add(latestRoundSettle.getIdleUserId());
                    visible.add(bankerUserId);
                }
            }
            return visible;
        }
        for (PendingAttackContext pending : pendingActions) {
            if ((pending.getStage() == GameEnums.PendingStage.WAIT_BANKER_RESPONSE
                    || pending.getStage() == GameEnums.PendingStage.WAIT_IDLE_DECISION
                    || pending.getStage() == GameEnums.PendingStage.WAIT_BANKER_GROUP)
                    && pending.getAttackType() == GameEnums.AttackType.ASK_RUN
                    && viewerUserId.equals(bankerUserId)) {
                visible.add(pending.getIdleUserId());
                continue;
            }
            if (pending.getStage() == GameEnums.PendingStage.WAIT_BANKER_GROUP
                    && (pending.getAttackType() == GameEnums.AttackType.STRONG
                    || pending.getAttackType() == GameEnums.AttackType.FISH)
                    && viewerUserId.equals(bankerUserId)) {
                visible.add(pending.getIdleUserId());
                continue;
            }
        }
        return visible;
    }

    private List<GameRoundSettle> filterVisibleRoundSettles(List<GameRoundSettle> roundSettles,
                                                            GameMatch match,
                                                            Long viewerUserId,
                                                            Long bankerUserId) {
        if (match.getMatchStatus().equals(GameEnums.MatchStatus.FINISHED.getCode())) {
            return roundSettles;
        }
        if (viewerUserId.equals(bankerUserId)) {
            return roundSettles;
        }
        return roundSettles.stream()
                .filter(item -> item.getIdleUserId().equals(viewerUserId))
                .toList();
    }

    private boolean shouldRevealRoundCards(GameRoundSettle settle) {
        GameEnums.AttackType attackType = GameEnums.AttackType.of(settle.getAttackType());
        if (attackType == GameEnums.AttackType.STRONG || attackType == GameEnums.AttackType.FISH) {
            return true;
        }
        if (attackType == GameEnums.AttackType.HIDDEN) {
            return settle.getBankerResponse() != null
                    && GameEnums.BankerResponseType.of(settle.getBankerResponse()) == GameEnums.BankerResponseType.OPEN;
        }
        if (attackType == GameEnums.AttackType.ASK_RUN) {
            return settle.getBankerResponse() != null
                    && GameEnums.BankerResponseType.of(settle.getBankerResponse()) == GameEnums.BankerResponseType.CALL_KILL;
        }
        return false;
    }

    private BattleContext buildBattleContext(Long roomId, GameMatch match, GameRoomUser currentIdle) {
        GameRoom room = roomService.getRoom(roomId);
        GroupedHand bankerGroupedHand = tryLoadGroupedHand(match.getMatchId(), room.getBankerUserId());
        GroupedHand idleGroupedHand = tryLoadGroupedHand(match.getMatchId(), currentIdle.getUserId());
        GameGroupFish bankerFish = findGroupFish(match.getMatchId(), room.getBankerUserId());
        GameGroupFish idleFish = findGroupFish(match.getMatchId(), currentIdle.getUserId());
        HandEvaluation bankerEvaluation = bankerGroupedHand == null || bankerFish == null
                ? null
                : gameRuleEngine.evaluate(bankerGroupedHand, bankerFish.getIsReportFish() == 1, bankerFish.getFishType());
        HandEvaluation idleEvaluation = idleGroupedHand == null || idleFish == null
                ? null
                : gameRuleEngine.evaluate(idleGroupedHand, idleFish.getIsReportFish() == 1, idleFish.getFishType());
        return new BattleContext(room, match, currentIdle, bankerGroupedHand, idleGroupedHand,
                bankerFish, idleFish, bankerEvaluation, idleEvaluation);
    }

    private void upsertGroupFish(Long matchId, Long userId, GroupedHand groupedHand, boolean reportFish, @Nullable GameEnums.FishType fishType) {
        GameGroupFish existing = gameGroupFishMapper.selectOne(new LambdaQueryWrapper<GameGroupFish>()
                .eq(GameGroupFish::getMatchId, matchId)
                .eq(GameGroupFish::getUserId, userId));
        if (existing == null) {
            existing = new GameGroupFish();
            existing.setMatchId(matchId);
            existing.setUserId(userId);
        }
        existing.setGroup1(gameRuleEngine.serializeCards(groupedHand.getGroup1()));
        existing.setGroup2(gameRuleEngine.serializeCards(groupedHand.getGroup2()));
        existing.setIsReportFish(reportFish ? 1 : 0);
        existing.setFishType(reportFish && fishType != null ? fishType.getCode() : null);
        existing.setGroupConfirmTime(LocalDateTime.now());
        existing.setReportFishTime(reportFish ? LocalDateTime.now() : null);
        if (existing.getId() == null) {
            gameGroupFishMapper.insert(existing);
        } else {
            gameGroupFishMapper.updateById(existing);
        }
    }

    private Map<Long, GameHandCard> getHandCards(Long matchId) {
        return gameHandCardMapper.selectList(new LambdaQueryWrapper<GameHandCard>()
                        .eq(GameHandCard::getMatchId, matchId))
                .stream()
                .collect(Collectors.toMap(GameHandCard::getUserId, item -> item));
    }

    private Map<Long, GameGroupFish> getGroupFishMap(Long matchId) {
        return gameGroupFishMapper.selectList(new LambdaQueryWrapper<GameGroupFish>()
                        .eq(GameGroupFish::getMatchId, matchId))
                .stream()
                .collect(Collectors.toMap(GameGroupFish::getUserId, item -> item));
    }

    private List<GameRoundSettle> getRoundSettles(Long matchId) {
        return gameRoundSettleMapper.selectList(new LambdaQueryWrapper<GameRoundSettle>()
                .eq(GameRoundSettle::getMatchId, matchId)
                .orderByAsc(GameRoundSettle::getId));
    }

    private List<GameRoomUser> getUnsettledIdleUsers(Long roomId, Long matchId) {
        Set<Long> settledIdleUserIds = getRoundSettles(matchId).stream()
                .map(GameRoundSettle::getIdleUserId)
                .collect(Collectors.toSet());
        return roomService.getRoomUsers(roomId).stream()
                .filter(item -> item.getUserRole().equals(GameEnums.RoomRole.IDLE.getCode()))
                .filter(item -> !settledIdleUserIds.contains(item.getUserId()))
                .toList();
    }

    private Integer findNextUnsettledIdleSort(Long roomId, Long matchId) {
        return getUnsettledIdleUsers(roomId, matchId).stream()
                .map(GameRoomUser::getIdleSort)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private Integer findNextAttackableIdleSort(Long roomId, Long matchId) {
        Set<Long> pendingIdleUserIds = getPendingActions(matchId).stream()
                .map(PendingAttackContext::getIdleUserId)
                .collect(Collectors.toSet());
        Integer nextAvailable = getUnsettledIdleUsers(roomId, matchId).stream()
                .filter(item -> !pendingIdleUserIds.contains(item.getUserId()))
                .map(GameRoomUser::getIdleSort)
                .sorted()
                .findFirst()
                .orElse(null);
        if (nextAvailable != null) {
            return nextAvailable;
        }
        return findNextUnsettledIdleSort(roomId, matchId);
    }

    private boolean hasIdleSettled(Long matchId, Long idleUserId) {
        return getRoundSettles(matchId).stream().anyMatch(item -> item.getIdleUserId().equals(idleUserId));
    }

    private GameRoomUser getCurrentIdleUser(Long roomId, Integer idleSort) {
        GameRoomUser currentIdle = gameRoomUserMapper.selectOne(new LambdaQueryWrapper<GameRoomUser>()
                .eq(GameRoomUser::getRoomId, roomId)
                .eq(GameRoomUser::getIdleSort, idleSort));
        if (currentIdle == null) {
            throw new BusinessException("Current idle seat does not exist");
        }
        return currentIdle;
    }

    private GameRoomUser getIdleUser(Long roomId, Long userId) {
        GameRoomUser idleUser = gameRoomUserMapper.selectOne(new LambdaQueryWrapper<GameRoomUser>()
                .eq(GameRoomUser::getRoomId, roomId)
                .eq(GameRoomUser::getUserId, userId)
                .eq(GameRoomUser::getUserRole, GameEnums.RoomRole.IDLE.getCode()));
        if (idleUser == null) {
            throw new BusinessException("This player is not an idle seat in the room");
        }
        return idleUser;
    }

    private GameHandCard getHandCard(Long matchId, Long userId) {
        GameHandCard handCard = gameHandCardMapper.selectOne(new LambdaQueryWrapper<GameHandCard>()
                .eq(GameHandCard::getMatchId, matchId)
                .eq(GameHandCard::getUserId, userId));
        if (handCard == null) {
            throw new BusinessException("Player hand does not exist");
        }
        return handCard;
    }

    private GameGroupFish getGroupFish(Long matchId, Long userId) {
        GameGroupFish groupFish = gameGroupFishMapper.selectOne(new LambdaQueryWrapper<GameGroupFish>()
                .eq(GameGroupFish::getMatchId, matchId)
                .eq(GameGroupFish::getUserId, userId));
        if (groupFish == null) {
            throw new BusinessException("Please finish grouping first");
        }
        return groupFish;
    }

    private @Nullable GameGroupFish findGroupFish(Long matchId, Long userId) {
        return gameGroupFishMapper.selectOne(new LambdaQueryWrapper<GameGroupFish>()
                .eq(GameGroupFish::getMatchId, matchId)
                .eq(GameGroupFish::getUserId, userId));
    }

    private GroupedHand loadGroupedHand(Long matchId, Long userId) {
        GameHandCard handCard = getHandCard(matchId, userId);
        GameGroupFish groupFish = getGroupFish(matchId, userId);
        return gameRuleEngine.validateAndBuildGrouping(
                gameRuleEngine.parseCards(handCard.getHandCards()),
                splitCardText(groupFish.getGroup1()),
                splitCardText(groupFish.getGroup2()),
                handCard.getChangeCard1(),
                handCard.getChangeCard2()
        );
    }

    private @Nullable GroupedHand tryLoadGroupedHand(Long matchId, Long userId) {
        GameGroupFish groupFish = findGroupFish(matchId, userId);
        if (groupFish == null) {
            return null;
        }
        GameHandCard handCard = getHandCard(matchId, userId);
        return gameRuleEngine.validateAndBuildGrouping(
                gameRuleEngine.parseCards(handCard.getHandCards()),
                splitCardText(groupFish.getGroup1()),
                splitCardText(groupFish.getGroup2()),
                handCard.getChangeCard1(),
                handCard.getChangeCard2()
        );
    }

    private GameMatch getActiveMatch(Long roomId) {
        GameMatch match = getLatestMatch(roomId);
        if (match == null || match.getMatchStatus().equals(GameEnums.MatchStatus.FINISHED.getCode())) {
            throw new BusinessException("There is no active match in this room");
        }
        return match;
    }

    private GameMatch getLatestMatch(Long roomId) {
        return gameMatchMapper.selectOne(new LambdaQueryWrapper<GameMatch>()
                .eq(GameMatch::getRoomId, roomId)
                .orderByDesc(GameMatch::getMatchId)
                .last("limit 1"));
    }

    private List<PendingAttackContext> getPendingActions(Long matchId) {
        Map<Long, PendingAttackContext> pendingMap = pendingAttackMap.get(matchId);
        if (pendingMap == null || pendingMap.isEmpty()) {
            return List.of();
        }
        return pendingMap.values().stream()
                .sorted(Comparator.comparing(PendingAttackContext::getCreatedAt)
                        .thenComparing(PendingAttackContext::getIdleSort))
                .toList();
    }

    private PendingAttackContext getPending(Long matchId, Long idleUserId, GameEnums.PendingStage stage) {
        Map<Long, PendingAttackContext> pendingMap = pendingAttackMap.get(matchId);
        PendingAttackContext pending = pendingMap == null ? null : pendingMap.get(idleUserId);
        if (pending == null || pending.getStage() != stage) {
            throw new BusinessException("No matching pending action was found");
        }
        return pending;
    }

    private void ensureIdleHasNoPending(Long matchId, Long idleUserId) {
        if (hasPendingForIdle(matchId, idleUserId)) {
            throw new BusinessException("This idle seat still has a pending request");
        }
    }

    private boolean hasPendingForIdle(Long matchId, Long idleUserId) {
        Map<Long, PendingAttackContext> pendingMap = pendingAttackMap.get(matchId);
        return pendingMap != null && pendingMap.containsKey(idleUserId);
    }

    private void putPending(PendingAttackContext pending) {
        pendingAttackMap.computeIfAbsent(pending.getMatchId(), key -> new ConcurrentHashMap<>())
                .put(pending.getIdleUserId(), pending);
    }

    private void removePending(Long matchId, Long idleUserId) {
        Map<Long, PendingAttackContext> pendingMap = pendingAttackMap.get(matchId);
        if (pendingMap == null) {
            return;
        }
        pendingMap.remove(idleUserId);
        if (pendingMap.isEmpty()) {
            pendingAttackMap.remove(matchId);
        }
    }

    private void refreshCurrentIdleSort(GameMatch match) {
        match.setCurrentIdleSort(findNextAttackableIdleSort(match.getRoomId(), match.getMatchId()));
        gameMatchMapper.updateById(match);
    }

    private void createPendingAction(BattleContext battleContext,
                                     GameEnums.AttackType attackType,
                                     GameEnums.PendingStage stage,
                                     @Nullable GameEnums.BankerResponseType bankerResponseType,
                                     String eventType) {
        putPending(PendingAttackContext.builder()
                .roomId(battleContext.room().getRoomId())
                .matchId(battleContext.match().getMatchId())
                .bankerUserId(battleContext.room().getBankerUserId())
                .idleUserId(battleContext.currentIdle().getUserId())
                .idleSort(battleContext.currentIdle().getIdleSort())
                .attackType(attackType)
                .stage(stage)
                .bankerResponseType(bankerResponseType)
                .createdAt(LocalDateTime.now())
                .build());
        battleContext.match().setCurrentIdleSort(findNextAttackableIdleSort(battleContext.room().getRoomId(), battleContext.match().getMatchId()));
        battleContext.match().setMatchStatus(GameEnums.MatchStatus.COMPARING.getCode());
        gameMatchMapper.updateById(battleContext.match());
        pushEvent(battleContext.room().getRoomId(), eventType);
    }

    private boolean isBankerReadyForComparison(BattleContext battleContext) {
        return battleContext.bankerGroupedHand() != null
                && battleContext.bankerGroupFish() != null
                && battleContext.bankerEvaluation() != null;
    }

    private void requireBankerReadyForComparison(BattleContext battleContext, String message) {
        if (!isBankerReadyForComparison(battleContext)) {
            throw new BusinessException(message);
        }
    }

    private void assertLoginUser(Long requestUserId, Long loginUserId) {
        if (!requestUserId.equals(loginUserId)) {
            throw new BusinessException(403, "You cannot operate on behalf of another user");
        }
    }

    private Set<Long> getOrCreateReadyUsers(Long roomId, List<GameRoomUser> roomUsers) {
        Set<Long> readyUsers = nextRoundReadyMap.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet());
        if (readyUsers.isEmpty()) {
            Map<Long, SysUser> userMap = roomService.loadUsers(roomUsers);
            roomUsers.stream()
                    .filter(item -> {
                        SysUser user = userMap.get(item.getUserId());
                        return user != null && user.getUserType().equals(GameEnums.UserType.AI.getCode());
                    })
                    .map(GameRoomUser::getUserId)
                    .forEach(readyUsers::add);
        }
        return readyUsers;
    }

    private List<String> splitCardText(String text) {
        return text == null || text.isBlank() ? List.of() : List.of(text.split(","));
    }

    private void pushEvent(Long roomId, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("roomId", roomId);
        payload.put("time", formatTime(LocalDateTime.now()));
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

    private String formatTime(@Nullable LocalDateTime time) {
        return time == null ? null : DATETIME_FORMATTER.format(time);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to serialize settlement detail");
        }
    }

    private record BattleContext(
            GameRoom room,
            GameMatch match,
            GameRoomUser currentIdle,
            @Nullable GroupedHand bankerGroupedHand,
            @Nullable GroupedHand idleGroupedHand,
            @Nullable GameGroupFish bankerGroupFish,
            @Nullable GameGroupFish idleGroupFish,
            @Nullable HandEvaluation bankerEvaluation,
            @Nullable HandEvaluation idleEvaluation
    ) {
    }
}
