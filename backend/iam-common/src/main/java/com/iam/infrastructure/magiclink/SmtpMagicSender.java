package com.iam.infrastructure.magiclink;

import com.iam.infrastructure.config.DynamicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * SMTP-based Magic Link sender using Spring JavaMail.
 *
 * Required config:
 *   iam.magic-link.provider=smtp
 *   iam.smtp.host=smtp.example.com
 *   iam.smtp.port=587
 *   iam.smtp.username=...
 *   iam.smtp.password=...
 *   iam.smtp.from=noreply@example.com
 */
@Slf4j
@Component
public class SmtpMagicSender implements MagicSender {

    private final DynamicConfig dynamicConfig;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final boolean starttls;

    public SmtpMagicSender(
            DynamicConfig dynamicConfig,
            @Value("${iam.magic-link.smtp-host:smtp.example.com}") String host,
            @Value("${iam.magic-link.smtp-port:587}") int port,
            @Value("${iam.magic-link.smtp-username:}") String username,
            @Value("${iam.magic-link.smtp-password:}") String password,
            @Value("${iam.magic-link.smtp-from:noreply@example.com}") String from,
            @Value("${iam.magic-link.smtp-starttls:true}") boolean starttls) {
        this.dynamicConfig = dynamicConfig;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.starttls = starttls;
    }

    private JavaMailSender mailSender() {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(dynamicConfig.getString("iam.magic-link.smtp-host", host));
        impl.setPort(dynamicConfig.getInt("iam.magic-link.smtp-port", port));
        impl.setUsername(dynamicConfig.getString("iam.magic-link.smtp-username", username));
        impl.setPassword(dynamicConfig.getString("iam.magic-link.smtp-password", password));
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable",
                String.valueOf(dynamicConfig.getBoolean("iam.magic-link.smtp-starttls", starttls)));
        return impl;
    }

    @Override
    public void send(String email, String link, int ttlMinutes) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(dynamicConfig.getString("iam.magic-link.smtp-from", from));
        msg.setTo(email);
        msg.setSubject("您的登录链接");
        msg.setText(String.format("点击登录（%d 分钟内有效）：%s\n\n如非您本人操作，请忽略本邮件。", ttlMinutes, link));
        mailSender().send(msg);
        log.info("[MAGIC-LINK-SMTP] sent to {}", email);
    }

    @Override
    public String providerName() { return "smtp"; }
}
