package com.shuiyu.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_room")
public class GameRoom {
    @TableId(value = "room_id", type = IdType.AUTO)
    private Long roomId;
    private String roomCode;
    private Long houseOwnerId;
    private Long bankerUserId;
    private Integer bankerType;
    private Integer maxIdle;
    private Integer currentIdle;
    private Integer roomStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
