package com.shuiyu.game;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.shuiyu.game.mapper")
@SpringBootApplication
public class ShuiyuGameBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShuiyuGameBackendApplication.class, args);
    }
}
