package com.foodmate.application.account.port.out;

/** 向用户发送一次性密码重置通知的外部端口。 */
public interface PasswordResetNotifier {
    /** 当前运行环境是否具备发送密码重置通知的能力。 */
    boolean isAvailable();

    /** 发送不包含明文密码的一次性重置令牌。 */
    void send(String recipient, String token);
}
