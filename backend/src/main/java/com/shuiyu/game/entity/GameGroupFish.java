package com.shuiyu.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_group_fish")
public class GameGroupFish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Long userId;
    private String group1;
    private String group2;
    private Integer isReportFish;
    private Integer fishType;
    private LocalDateTime groupConfirmTime;
    private LocalDateTime reportFishTime;
}
