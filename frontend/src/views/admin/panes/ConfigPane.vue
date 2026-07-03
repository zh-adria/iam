<template>
  <div>
    <div class="config-summary">
      <div class="config-header">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
        <h3>系统配置</h3>
      </div>
      <div class="config-note glass-card">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
        <span>{{ config.note || '配置说明' }}</span>
      </div>
    </div>

    <div class="config-section glass-card">
      <h4 class="section-title">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg>
        认证协议开关
      </h4>
      <p class="section-desc">认证协议在 <code>application.yml</code> 中配置，需重启生效</p>

      <div class="protocol-grid">
        <div v-for="p in protocols" :key="p.name" class="protocol-item">
          <div class="protocol-info">
            <span class="protocol-name">{{ p.name }}</span>
            <span class="protocol-detail">{{ p.detail }}</span>
          </div>
          <span class="protocol-badge active">已启用</span>
        </div>
      </div>
    </div>

    <p class="hint">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
      租户级 LDAP 配置可在「租户管理」中按租户编辑；协议开关需编辑 application.yml 后重启。
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '../../../api/admin'

const config = ref<Record<string, unknown>>({ note: '加载中...' })
onMounted(async () => { config.value = await adminApi.config() })

const protocols = [
  { name: 'OAuth2 授权服务器', detail: '/oauth/{authorize,token,userinfo,introspect,revoke,jwks,.well-known}' },
  { name: 'OIDC', detail: 'id_token (HS256) — 配置 RS256 + JWKS 需切换 jwt.secret 为 RSA 密钥对' },
  { name: 'SAML 2.0 SP', detail: 'iam.saml.idp.metadata-url 或 entity-id/sso-url — 改后重启' },
  { name: 'LDAP/AD', detail: 'iam.ldap.url — 空则禁用；按租户 LDAP 在租户管理配置' },
  { name: 'CAS SSO', detail: 'iam.cas.server-url — 空则禁用' },
  { name: '社交登录', detail: 'iam.social.{wechat,alipay,qq,dingtalk,wecom}.*' },
  { name: '短信验证码', detail: 'iam.sms.provider=stub（控制台打印）— 接阿里云短信 SDK 替换' },
  { name: 'Magic Link', detail: 'iam.magic-link.base-url — 邮件 sender 为 stub' },
  { name: 'WebAuthn / FIDO2', detail: 'iam.webauthn.enabled=false — 需接入 spring-security-webauthn' },
  { name: 'Kerberos / SPNEGO', detail: 'iam.kerberos.enabled=false — 需 JAAS + krb5.conf' },
  { name: 'SCIM 2.0', detail: 'iam.scim.enabled=false — /scim/v2/Users 占位' },
]
</script>

<style scoped>
.config-summary { margin-bottom: 24px; }
.config-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.config-header svg { color: var(--accent); }
.config-header h3 {
  font-family: var(--font-heading);
  font-size: 1.15rem;
  font-weight: 700;
  margin: 0;
  color: var(--text-primary);
}

.config-note {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: rgba(0, 212, 255, 0.06) !important;
  border-color: rgba(0, 212, 255, 0.15) !important;
  font-size: 0.9rem;
  color: var(--accent);
}

.config-section {
  padding: 24px;
  margin-bottom: 20px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: 1rem;
  margin: 0 0 6px;
  color: var(--text-primary);
}
.section-title svg { color: var(--accent); }
.section-desc {
  color: var(--text-muted);
  font-size: 0.82rem;
  margin-bottom: 20px;
}

.protocol-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
@media (max-width: 800px) { .protocol-grid { grid-template-columns: 1fr; } }

.protocol-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  transition: all var(--dur-fast) var(--ease-out);
}
.protocol-item:hover {
  border-color: var(--border-hover);
  background: rgba(255, 255, 255, 0.04);
}
.protocol-info {
  flex: 1;
  min-width: 0;
}
.protocol-name {
  display: block;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
}
.protocol-detail {
  display: block;
  font-size: 0.75rem;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.protocol-badge {
  flex-shrink: 0;
  padding: 3px 12px;
  border-radius: 100px;
  font-size: 0.72rem;
  font-weight: 600;
  font-family: var(--font-mono);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.protocol-badge.active {
  background: rgba(46, 213, 115, 0.1);
  color: var(--success);
  border: 1px solid rgba(46, 213, 115, 0.2);
}

.hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 0.82rem;
  padding: 0 4px;
}
.hint svg { flex-shrink: 0; color: var(--text-muted); }
</style>