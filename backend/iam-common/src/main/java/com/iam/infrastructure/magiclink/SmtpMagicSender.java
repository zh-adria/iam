package com.iam.infrastructure.magiclink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "iam.magic-link.provider", havingValue = "smtp")
public class SmtpMagicSender implements MagicSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMagicSender(
            @Value("${iam.smtp.host:smtp.example.com}") String host,
            @Value("${iam.smtp.port:587}") int port,
            @Value("${iam.smtp.username:}") String username,
            @Value("${iam.smtp.password:}") String password,
            @Value("${iam.smtp.from:noreply@example.com}") String from,
            @Value("${iam.smtp.starttls:true}") boolean starttls) {
        this.from = from;
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(port);
        impl.setUsername(username);
        impl.setPassword(password);
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        this.mailSender = impl;
    }

    @Override
    public void send(String email, String link, int ttlMinutes) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(email);
        msg.setSubject("您的登录链接");
        msg.setText(String.format("点击登录（%d 分钟内有效）：%s\n\n如非您本人操作，请忽略本邮件。", ttlMinutes, link));
        mailSender.send(msg);
        log.info("[MAGIC-LINK-SMTP] sent to {}", email);
    }

    @Override
    public String providerName() { return "smtp"; }
}
