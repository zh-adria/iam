<template>
  <el-card>
    <h3>系统配置</h3>
    <el-alert :title="String(config.note)" type="info" :closable="false" show-icon />
    <el-divider />
    <h4>认证协议开关（application.yml）</h4>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="OAuth2 授权服务器">已启用 — /oauth/{authorize,token,userinfo,introspect,revoke,jwks,.well-known}</el-descriptions-item>
      <el-descriptions-item label="OIDC">已启用 id_token (HS256) — 配置 RS256 + JWKS 需切换 jwt.secret 为 RSA 密钥对</el-descriptions-item>
      <el-descriptions-item label="SAML 2.0 SP">iam.saml.idp.metadata-url 或 entity-id/sso-url — 改后重启</el-descriptions-item>
      <el-descriptions-item label="LDAP/AD">iam.ldap.url — 空则禁用；按租户 LDAP 在租户管理配置</el-descriptions-item>
      <el-descriptions-item label="CAS SSO">iam.cas.server-url — 空则禁用</el-descriptions-item>
      <el-descriptions-item label="社交登录">iam.social.{wechat,alipay,qq,dingtalk,wecom}.* — 空 appId 则禁用</el-descriptions-item>
      <el-descriptions-item label="短信验证码">iam.sms.provider=stub（控制台打印）— 接阿里云短信 SDK 替换</el-descriptions-item>
      <el-descriptions-item label="Magic Link">iam.magic-link.base-url — 邮件 sender 为 stub</el-descriptions-item>
      <el-descriptions-item label="WebAuthn/FIDO2">iam.webauthn.enabled=false — 需接入 spring-security-webauthn</el-descriptions-item>
      <el-descriptions-item label="Kerberos/SPNEGO">iam.kerberos.enabled=false — 需 JAAS + krb5.conf</el-descriptions-item>
      <el-descriptions-item label="SCIM 2.0">iam.scim.enabled=false — /scim/v2/Users 占位</el-descriptions-item>
    </el-descriptions>
    <el-divider />
    <p class="hint">租户级 LDAP 配置可在「租户管理」中按租户编辑；系统级协议开关需编辑 application.yml 后重启。</p>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '../../../api/admin'

const config = ref<Record<string, unknown>>({ note: '加载中...' })
onMounted(async () => { config.value = await adminApi.config() })
</script>

<style scoped>
.hint { color: #909399; font-size: 13px; }
</style>
