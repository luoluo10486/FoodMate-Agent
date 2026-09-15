package com.foodmate.infrastructure.notification;

import com.foodmate.application.account.port.out.PasswordResetNotifier;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** 基于 SMTP 的密码重置通知适配器；不会记录或返回明文重置令牌。 */
@Component
public class SmtpPasswordResetNotifier implements PasswordResetNotifier {
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String webBaseUrl;

    public SmtpPasswordResetNotifier(
            ObjectProvider<JavaMailSender> mailProvider,
            @Value("${spring.mail.username:}") String mailFrom,
            @Value("${foodmate.web.base-url:http://localhost:5173}") String webBaseUrl) {
        this.mailSender = mailProvider.getIfAvailable();
        this.mailFrom = mailFrom;
        this.webBaseUrl = webBaseUrl;
    }

    @Override
    public boolean isAvailable() {
        return mailSender != null && mailFrom != null && !mailFrom.isBlank();
    }

    @Override
    public void send(String recipient, String token) {
        if (!isAvailable())
            throw new BusinessException(ErrorCode.COORDINATION_UNAVAILABLE, "密码重置通知服务暂不可用");
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String url = webBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + token;
            helper.setFrom(mailFrom);
            helper.setTo(recipient);
            helper.setSubject("FoodMate-重置密码");
            helper.setText(textBody(url), htmlBody(url, token));
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException | MailException exception) {
            throw new BusinessException(ErrorCode.COORDINATION_UNAVAILABLE, "密码重置通知发送失败");
        }
    }

    private static String textBody(String url) {
        return "你好，\n\n我们收到了一份重置 FoodMate 密码的请求。\n"
                + "请打开下面的链接，按页面提示设置新的登录密码：\n"
                + url
                + "\n\n链接 15 分钟内有效，并且只能使用一次。\n"
                + "如果这不是你发起的请求，请直接忽略这封邮件。\n";
    }

    private static String htmlBody(String url, String token) {
        return "<div style='margin:0;background:#f4f7fb;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Microsoft YaHei,sans-serif;color:#172033'>"
                + "<div style='max-width:600px;margin:auto;background:#fff;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden'>"
                + "<div style='background:#1677ff;padding:24px 32px;color:#fff'><div style='font-size:24px;font-weight:700'>FoodMate</div><div style='margin-top:6px;font-size:13px;opacity:.9'>智能饮食与生活助手</div></div>"
                + "<div style='padding:32px'><div style='font-size:22px;font-weight:700'>你好，帮你把密码重置好</div>"
                + "<p style='margin:18px 0;color:#526076;line-height:1.8'>管理员已为你的账号发起密码重置。点击下面的按钮设置新的登录密码。</p>"
                + "<a href='"
                + url
                + "' style='display:inline-block;background:#1677ff;color:#fff;text-decoration:none;border-radius:7px;padding:12px 24px;font-weight:600'>去重置密码</a>"
                + "<div style='margin-top:26px;padding:16px;background:#f6f8fb;border-radius:8px;color:#526076;font-size:13px;line-height:1.8'>"
                + "按钮打不开？请复制下面的一次性令牌：<br><span style='font-family:Consolas,monospace;color:#172033;word-break:break-all'>"
                + token
                + "</span></div>"
                + "<p style='margin:22px 0 0;color:#7b8798;font-size:13px;line-height:1.8'>链接和令牌 15 分钟内有效，并且只能使用一次。FoodMate 不会通过邮件向你索要密码。</p></div>"
                + "<div style='border-top:1px solid #edf0f4;padding:16px 32px;color:#98a2b3;font-size:12px'>此邮件由系统自动发送，请勿直接回复。</div></div></div>";
    }
}
