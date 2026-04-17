package com.shuiyu.game.controller;

import com.shuiyu.game.common.ApiResponse;
import com.shuiyu.game.dto.ResponseModels;
import com.shuiyu.game.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {
    private final HistoryService historyService;

    @GetMapping("/list")
    public ApiResponse<List<ResponseModels.HistoryItemView>> list(@RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(historyService.list(loginUserId));
    }

    @GetMapping("/detail/{matchId}")
    public ApiResponse<ResponseModels.HistoryDetailView> detail(@PathVariable Long matchId,
                                                                @RequestAttribute("loginUserId") Long loginUserId) {
        return ApiResponse.success(historyService.detail(matchId, loginUserId));
    }
}
