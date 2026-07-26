package com.foodmate.gateway;

/**
 * MQ 消息消费结果。消费端在业务事务提交后才返回 {@link #ACK}（ADR-0005）。
 *
 * <p>三类结果对应 §5.16 的失败分类：可重试异常走 Broker 重投，schema/digest/权限/fencing
 * 这类确定性错误直接 rejection，不做无意义重试。
 */
public enum MqConsumeDecision {
    /** 业务事务已提交，或消息是可安全忽略的重复投递。 */
    ACK,
    /** 暂时性失败（数据库不可用、缺少前序事件），交给 Broker 重投。 */
    RETRY,
    /** 确定性错误，重试不可能成功；已记录拒绝原因，直接确认以免占用重试位。 */
    REJECT
}
