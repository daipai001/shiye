package com.shiye.user.module.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${shiye.mail.enabled:false}")
    private boolean enabled;

    @Value("${shiye.mail.from:no-reply@shiye.local}")
    private String from;

    @Value("${shiye.mail.public-url:http://localhost:15173}")
    private String publicUrl;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void sendVerification(String to, String token) {
        send(to, "验证你的拾页邮箱",
                "请打开下面的链接验证邮箱：\n" + link("/verify-email?token=" + token)
                        + "\n\n如果不是你本人操作，请忽略这封邮件。");
    }

    public void sendPasswordReset(String to, String token) {
        send(to, "重置拾页密码",
                "请打开下面的链接重置密码（30 分钟内有效）：\n"
                        + link("/reset-password?token=" + token)
                        + "\n\n如果不是你本人操作，请忽略这封邮件。");
    }

    public void sendTeamInvite(String to, String teamName, String code) {
        send(to, "邀请你加入拾页团队「" + teamName + "」",
                "团队邀请码：" + code + "\n\n"
                        + "登录拾页后在「团队 → 加入团队」输入邀请码即可。邀请码 7 天内有效，单次使用。");
    }

    private void send(String to, String subject, String text) {
        if (!enabled) {
            log.info("mail disabled, skip '{}' to {}", subject, to);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("mail enabled but JavaMailSender unavailable, skip '{}' to {}", subject, to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            log.info("mail sent: '{}' to {}", subject, to);
        } catch (RuntimeException ex) {
            // 邮件失败不能阻断注册/邀请等主流程
            log.warn("mail send failed '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }

    private String link(String path) {
        String base = publicUrl == null ? "" : publicUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}
