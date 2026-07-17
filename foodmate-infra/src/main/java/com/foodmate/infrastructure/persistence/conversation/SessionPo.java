package com.foodmate.infrastructure.persistence.conversation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.BasePo;

@TableName("sessions")
public class SessionPo extends BasePo {
    @TableId("session_id") public Long sessionId;
    public Long tenantId;
    public Long userId;
    public String title;
    public String mode;
    public String status;
    public java.time.Instant lastMessageAt;
}
