package com.shuiyu.game.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shuiyu.game.dto.ResponseModels;
import com.shuiyu.game.entity.GameGlobalSettle;
import com.shuiyu.game.entity.GameMatch;
import com.shuiyu.game.entity.GameRoom;
import com.shuiyu.game.entity.GameRoomUser;
import com.shuiyu.game.mapper.GameGlobalSettleMapper;
import com.shuiyu.game.mapper.GameMatchMapper;
import com.shuiyu.game.mapper.GameRoomMapper;
import com.shuiyu.game.mapper.GameRoomUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoryService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GameRoomUserMapper gameRoomUserMapper;
    private final GameMatchMapper gameMatchMapper;
    private final GameGlobalSettleMapper gameGlobalSettleMapper;
    private final GameRoomMapper gameRoomMapper;
    private final GameService gameService;

    public List<ResponseModels.HistoryItemView> list(Long loginUserId) {
        List<GameRoomUser> memberships = gameRoomUserMapper.selectList(new LambdaQueryWrapper<GameRoomUser>()
                .eq(GameRoomUser::getUserId, loginUserId));
        Set<Long> roomIds = memberships.stream().map(GameRoomUser::getRoomId).collect(Collectors.toSet());
        if (roomIds.isEmpty()) {
            return List.of();
        }
        List<GameMatch> matches = gameMatchMapper.selectList(new LambdaQueryWrapper<GameMatch>()
                .in(GameMatch::getRoomId, roomIds)
                .orderByDesc(GameMatch::getMatchId));
        List<Long> matchIds = matches.stream().map(GameMatch::getMatchId).toList();
        Map<Long, GameGlobalSettle> globalMap = matchIds.isEmpty() ? Map.of() :
                gameGlobalSettleMapper.selectList(new LambdaQueryWrapper<GameGlobalSettle>()
                                .in(GameGlobalSettle::getMatchId, matchIds))
                        .stream()
                        .collect(Collectors.toMap(GameGlobalSettle::getMatchId, item -> item));
        Map<Long, GameRoom> roomMap = gameRoomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(GameRoom::getRoomId, item -> item));
        return matches.stream().map(match -> {
            GameGlobalSettle global = globalMap.get(match.getMatchId());
            GameRoom room = roomMap.get(match.getRoomId());
            return new ResponseModels.HistoryItemView(
                    match.getMatchId(),
                    room == null ? "" : room.getRoomCode(),
                    match.getStartTime() == null ? null : DATETIME_FORMATTER.format(match.getStartTime()),
                    match.getEndTime() == null ? null : DATETIME_FORMATTER.format(match.getEndTime()),
                    global == null ? 0 : global.getBankerWinCount(),
                    global == null ? 0 : global.getIdleWinCount(),
                    global == null ? 0 : global.getDrawCount(),
                    global == null ? 1 : global.getMaxRate()
            );
        }).toList();
    }

    public ResponseModels.HistoryDetailView detail(Long matchId, Long loginUserId) {
        ResponseModels.GameStateView state = gameService.getStateByMatch(matchId, loginUserId);
        return new ResponseModels.HistoryDetailView(state, state.roundSettles(), state.globalSettle());
    }
}
