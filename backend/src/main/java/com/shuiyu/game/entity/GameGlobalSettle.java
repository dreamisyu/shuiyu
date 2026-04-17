package com.shuiyu.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_global_settle")
public class GameGlobalSettle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Integer bankerWinCount;
    private Integer idleWinCount;
    private Integer drawCount;
    private Integer maxRate;
    private String settleDetail;
    private LocalDateTime settleTime;
}
