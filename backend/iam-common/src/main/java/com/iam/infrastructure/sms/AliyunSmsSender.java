package com.iam.infrastructure.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.iam.infrastructure.config.DynamicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aliyun SMS ( Dysmsapi 2017-05-25 ) sender.
 *
 * Required config:
 *   iam.sms.provider=aliyun
 *   iam.sms.aliyun-access-key=...
 *   iam.sms.aliyun-secret=...
 *   iam.sms.aliyun-sign-name=...
 *   iam.sms.aliyun-template-code=...
 *
 * Template example: "您的验证码为：${code}，5 分钟内有效。"
 * Passed as JSON: {"code":"123456"}
 */
@Slf4j
@Component
public class AliyunSmsSender implements SmsSender {

    private final DynamicConfig dynamicConfig;
    private final String accessKey;
    private final String secret;
    private final String signName;
    private final String templateCode;
    private final String endpoint;

    public AliyunSmsSender(
            DynamicConfig dynamicConfig,
            @Value("${iam.sms.aliyun-access-key:}") String accessKey,
            @Value("${iam.sms.aliyun-secret:}") String secret,
            @Value("${iam.sms.aliyun-sign-name:}") String signName,
            @Value("${iam.sms.aliyun-template-code:}") String templateCode,
            @Value("${iam.sms.aliyun-endpoint:dysmsapi.aliyuncs.com}") String endpoint) {
        this.dynamicConfig = dynamicConfig;
        this.accessKey = accessKey;
        this.secret = secret;
        this.signName = signName;
        this.templateCode = templateCode;
        this.endpoint = endpoint;
    }

    @Override
    public String send(String phone, String code) throws Exception {
        String actualAccessKey = dynamicConfig.getString("iam.sms.aliyun-access-key", accessKey);
        String actualSecret = dynamicConfig.getString("iam.sms.aliyun-secret", secret);
        String actualSignName = dynamicConfig.getString("iam.sms.aliyun-sign-name", signName);
        String actualTemplateCode = dynamicConfig.getString("iam.sms.aliyun-template-code", templateCode);
        String actualEndpoint = dynamicConfig.getString("iam.sms.aliyun-endpoint", endpoint);
        Config config = new Config()
                .setAccessKeyId(actualAccessKey)
                .setAccessKeySecret(actualSecret)
                .setEndpoint(actualEndpoint);
        Client client = new Client(config);
        SendSmsRequest req = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(actualSignName)
                .setTemplateCode(actualTemplateCode)
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        SendSmsResponse resp = client.sendSms(req);
        log.info("[SMS-ALIYUN] send to {}: requestId={} code={}", phone,
                resp.getBody().requestId, resp.getBody().code);
        return resp.getBody().bizId;
    }

    @Override
    public String providerName() { return "aliyun"; }
}
