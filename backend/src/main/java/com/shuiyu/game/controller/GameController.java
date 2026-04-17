package com.shuiyu.game.controller;

import com.shuiyu.game.common.ApiResponse;
import com.shuiyu.game.dto.RequestModels;
import com.shuiyu.game.dto.ResponseModels;
import com.shuiyu.game.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;

    @PostMapping("/deal")
    public ApiResponse<ResponseModels.GameStateView> deal(@RequestParam Long roomId,
                                                          @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(gameService.deal(roomId, loginUserId));
    }

    @PostMapping("/group")
    public ApiResponse<ResponseModels.GameStateView> group(@Valid @RequestBody RequestModels.GroupRequest request,
                                                           @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(gameService.submitGroup(request, loginUserId));
    }

    @PostMapping("/reportFish")
    public ApiResponse<ResponseModels.GameStateView> reportFish(@Valid @RequestBody RequestModels.ReportFishRequest request,
                                                                @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(gameService.reportFish(request, loginUserId));
    }

    @PostMapping("/attack")
    public ApiResponse<ResponseModels.GameStateView> attack(@Valid @RequestBody RequestModels.AttackRequest request,
                                                            @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(gameService.attack(request, loginUserId));
    }

    @PostMapping("/respond")
    public ApiResponse<ResponseModels.GameStateView> respond(@Valid @RequestBody RequestModels.BankerResponseRequest request,
                                                             @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(gameService.bankerRespond(request, loginUserId));
    }

    @PostMapping("/decide")
    public ApiResponse<ResponseModels.GameStateView> decide(@Valid @RequestBody RequestModels.IdleDecisionRequest request,
                                                            @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(gameService.idleDecide(request, loginUserId));
    }

    @PostMapping("/processPending")
    public ApiResponse<ResponseModels.GameStateView> processPending(
            @Valid @RequestBody RequestModels.ProcessPendingRequest request,
            @RequestAttribute("loginUserId") Long loginUserId
    ) {
        return ApiResponse.success(gameService.processPending(request, loginUserId));
    }

    @PostMapping("/readyNextRound")
    public ApiResponse<ResponseModels.GameStateView> readyNextRound(
            @Valid @RequestBody RequestModels.ReadyNextRoundRequest request,
            @RequestAttribute("loginUserId") Long loginUserId
    ) {
        return ApiResponse.success(gameService.readyNextRound(request, loginUserId));
    }

    @GetMapping("/state")
    public ApiResponse<ResponseModels.GameStateView> state(@RequestParam Long roomId,
                                                           @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(gameService.getState(roomId, loginUserId));
    }
}
