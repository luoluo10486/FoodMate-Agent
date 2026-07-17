package com.foodmate.infrastructure.persistence.account;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.BasePo;

@TableName("auth_refresh_tokens")
public class AuthRefreshTokenPo extends BasePo {
    @TableId("refresh_token_id") public Long refreshTokenId;
    public Long userId;
    public String tokenHash;
    public String deviceId;
    public String userAgent;
    public String ipAddress;
    public java.time.Instant expiresAt;
    public java.time.Instant revokedAt;
    public Long rotatedFromTokenId;
}
