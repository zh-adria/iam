<template>
  <div v-loading="loading">
    <div class="config-summary">
      <div class="config-header">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color:var(--accent)"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
        <div>
          <h3>系统配置</h3>
          <p class="section-desc">认证协议在 <code>application.yml</code> 中配置，需重启生效</p>
        </div>
      </div>

      <div v-if="config.note" class="config-note">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
        <span>{{ config.note }}</span>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="认证协议" name="protocol">
        <div class="config-grid">
          <div v-for="p in protocols" :key="p.name" class="config-item glass-card">
            <div class="protocol-item">
              <div>
                <div class="config-key">{{ p.name }}</div>
                <div class="protocol-detail">{{ p.detail }}</div>
              </div>
              <span class="neo-tag success">已启用</span>
            </div>
          </div>
        </div>
        <p class="hint">租户级 LDAP 配置可在「租户管理」中按租户编辑；协议开关需编辑 application.yml 后重启。</p>
      </el-tab-pane>
      <el-tab-pane label="全部配置" name="all">
        <div class="config-grid">
          <div v-for="(v, k) in flatCfg" :key="k" class="config-item glass-card">
            <div class="config-key">{{ k }}</div>
            <div class="config-value">
              <span v-if="typeof v === 'boolean'">
                <span :class="['neo-tag', v ? 'success' : 'danger']">{{ v ? 'YES' : 'NO' }}</span>
              </span>
              <span v-else-if="typeof v === 'object'"><pre class="inline-pre">{{ JSON.stringify(v, null, 2) }}</pre></span>
              <span v-else>{{ v }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '../../../api/admin'

const loading = ref(false)
const config = ref<Record<string, any>>({ note: '加载中...' })
const activeTab = ref('protocol')
const flatCfg = computed(() => {
  const { note, ...rest } = config.value
  return rest
})

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
.section-desc { font-size: 0.82rem; color: var(--text-muted); margin-top: 2px; }
.config-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  background: var(--accent-glow);
  border: 1px solid var(--accent-glow-strong);
  border-radius: var(--radius-md);
  color: var(--accent);
  font-size: 0.88rem;
}
.protocol-detail { font-size: 0.78rem; color: var(--text-muted); margin-top: 2px; }
.protocol-item { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; width: 100%; }
.hint { display: flex; align-items: center; gap: 8px; margin-top: 16px; color: var(--text-muted); font-size: 0.78rem; }
.inline-pre { margin: 0; font-family: var(--font-mono); font-size: 0.78rem; white-space: pre-wrap; }
</style>
