package com.shuiyu.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_round_settle")
public class GameRoundSettle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Long bankerUserId;
    private Long idleUserId;
    private Integer attackType;
    private Integer bankerResponse;
    private Integer result;
    private Integer rate;
    private Long drinkUserId;
    private LocalDateTime settleTime;
}
