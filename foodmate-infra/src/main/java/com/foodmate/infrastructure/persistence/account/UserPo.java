package com.foodmate.infrastructure.persistence.account;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.BasePo;

@TableName("users")
public class UserPo extends BasePo {
    @TableId("user_id") public Long userId;
    public Long tenantId;
    public String userNo;
    public String username;
    public String email;
    public String passwordHash;
    public String nickname;
    public String role;
    public String avatarUrl;
    public String status;
    public java.time.Instant lastLoginAt;
    public java.time.Instant passwordUpdatedAt;
    public Integer loginFailedCount;
    public java.time.Instant lockedUntil;
}
