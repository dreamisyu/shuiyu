package com.shuiyu.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_match")
public class GameMatch {
    @TableId(value = "match_id", type = IdType.AUTO)
    private Long matchId;
    private Long roomId;
    private Long bankerUserId;
    private Integer matchStatus;
    private Integer currentIdleSort;
    private Integer bankerGroupLocked;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
