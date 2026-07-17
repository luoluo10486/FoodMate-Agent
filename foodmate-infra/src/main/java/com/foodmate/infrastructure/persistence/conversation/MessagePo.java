package com.foodmate.infrastructure.persistence.conversation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.BasePo;

@TableName("messages")
public class MessagePo extends BasePo {
    @TableId("message_id") public Long messageId;
    public Long sessionId;
    public Long agentRunId;
    public String role;
    public String content;
    public String structuredPayload;
    public Integer sequenceNo;
}
