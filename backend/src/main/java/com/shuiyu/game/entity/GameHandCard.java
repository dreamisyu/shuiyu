package com.shuiyu.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_hand_card")
public class GameHandCard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Long userId;
    private String handCards;
    private String changeCard1;
    private String changeCard2;
    private LocalDateTime createTime;
}
