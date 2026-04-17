package com.shuiyu.game.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shuiyu.game.common.BusinessException;
import com.shuiyu.game.dto.RequestModels;
import com.shuiyu.game.dto.ResponseModels;
import com.shuiyu.game.entity.SysUser;
import com.shuiyu.game.mapper.SysUserMapper;
import com.shuiyu.game.model.GameEnums;
import com.shuiyu.game.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserMapper sysUserMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public ResponseModels.AuthResponse register(RequestModels.RegisterRequest request) {
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setAvatar("");
        user.setPhone("");
        user.setUserType(GameEnums.UserType.REAL.getCode());
        sysUserMapper.insert(user);
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new ResponseModels.AuthResponse(token, toProfile(user));
    }

    public ResponseModels.AuthResponse login(RequestModels.LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new ResponseModels.AuthResponse(token, toProfile(user));
    }

    public SysUser getUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    public ResponseModels.UserProfile toProfile(SysUser user) {
        return new ResponseModels.UserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getUserType()
        );
    }
}
