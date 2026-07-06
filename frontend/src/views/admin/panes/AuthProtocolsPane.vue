<template>
  <div class="auth-protocols">
    <section class="overview-grid">
      <article v-for="card in overviewCards" :key="card.key" class="protocol-card">
        <div class="card-top">
          <span class="card-icon" v-html="card.icon" />
          <span :class="['neo-tag', card.ready ? 'success' : 'dim']">{{ card.ready ? '已配置' : '未完成' }}</span>
        </div>
        <h3>{{ card.title }}</h3>
        <p>{{ card.desc }}</p>
        <div class="progress-line"><span :style="{ width: `${card.percent}%` }" /></div>
      </article>
    </section>

    <el-tabs v-model="activeTab" class="protocol-tabs">
      <el-tab-pane label="SAML" name="saml">
        <PaneToolbar :show-create="true" create-label="新建 IdP" @create="editSaml(null)" />
        <el-table :data="samlRows" v-loading="samlLoading" style="width:100%">
          <el-table-column prop="tenantCode" label="租户" width="120" />
          <el-table-column prop="registrationId" label="Registration ID" min-width="160" />
          <el-table-column prop="idpEntityId" label="IdP Entity ID" min-width="220" show-overflow-tooltip />
          <el-table-column prop="idpMetadataUrl" label="Metadata URL" min-width="220" show-overflow-tooltip />
          <el-table-column prop="metadataLastRefreshedAt" label="Metadata 刷新" width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span :class="['neo-tag', row.enabled ? 'success' : 'dim']">{{ row.enabled ? '启用' : '停用' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="right">
            <template #default="{ row }">
              <el-button size="small" plain type="success" @click="downloadMetadata(row)">SP Metadata</el-button>
              <el-button size="small" plain type="primary" @click="editSaml(row)">编辑</el-button>
              <el-button size="small" plain type="danger" @click="deleteSaml(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="SCIM" name="scim">
        <div class="scim-section">
          <section class="scim-cards">
            <div class="scim-card">
              <h4>SCIM 端点</h4>
              <p class="scim-url">Base URL: <code>{{ scimBaseUrl }}</code></p>
              <p class="scim-hint">对外部系统使用 Bearer Token 认证访问</p>
            </div>
            <div class="scim-card">
              <h4>Provisioner Tokens</h4>
              <PaneToolbar :show-create="true" create-label="创建 Token" @create="editScimToken(null)" />
              <el-table :data="scimTokens" v-loading="scimTokenLoading" style="width:100%">
                <el-table-column prop="name" label="名称" min-width="160" />
                <el-table-column label="Token 前缀" width="140">
                  <template #default="{ row }">
                    <code>{{ row.tokenPrefix }}...</code>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="lastUsedAt" label="最后使用" width="160" />
                <el-table-column label="操作" width="120" align="right">
                  <template #default="{ row }">
                    <el-button size="small" plain type="danger" @click="revokeScimToken(row)">吊销</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </section>
          <section class="scim-cards" style="margin-top:20px">
            <div class="scim-card">
              <h4>SCIM 调试</h4>
              <div class="scim-debug">
                <div class="debug-row">
                  <el-select v-model="scimDebugMethod" style="width:110px">
                    <el-option label="GET" value="GET" />
                    <el-option label="POST" value="POST" />
                    <el-option label="PATCH" value="PATCH" />
                    <el-option label="DELETE" value="DELETE" />
                  </el-select>
                  <el-input v-model="scimDebugPath" placeholder="/scim/v2/Users" style="flex:1" />
                  <el-input v-model="scimDebugToken" placeholder="Bearer Token" style="width:200px" show-password />
                  <el-button type="primary" :loading="scimDebugLoading" @click="sendScimDebug">发送</el-button>
                </div>
                <el-input v-model="scimDebugBody" type="textarea" :rows="4" placeholder='{"schemas":["urn:ietf:params:scim:api:messages:2.0:ListResponse"]}' />
                <div v-if="scimDebugResult" class="debug-result">
                  <div class="debug-status">状态: {{ scimDebugStatus }}</div>
                  <pre>{{ scimDebugResult }}</pre>
                </div>
              </div>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane v-for="group in configGroups" :key="group.key" :label="group.title" :name="group.key">
        <div v-if="group.key === 'ldap'" class="config-field" style="margin-bottom:12px">
          <div class="field-head"><span>LDAP 预设模板</span></div>
          <el-select v-model="ldapPreset" placeholder="选择预设" style="width:100%" @change="applyLdapPreset">
            <el-option label="通用 OpenLDAP" value="generic" />
            <el-option label="Active Directory (用户绑定)" value="active-directory" />
            <el-option label="Active Directory (DN 绑定)" value="active-directory-bind" />
          </el-select>
        </div>
        <div class="config-grid">
          <div v-for="field in group.fields" :key="field.key" class="config-field">
            <div class="field-head">
              <span>{{ field.label }}</span>
              <span v-if="field.required" class="required-dot" />
            </div>
            <el-input
              v-model="drafts[field.key]"
              :type="field.type === 'secret' ? 'password' : 'text'"
              :show-password="field.type === 'secret'"
              :placeholder="field.placeholder || field.key"
            />
          </div>
        </div>
        <div class="actions-row">
          <el-button v-if="group.key === 'ldap'" type="default" :loading="testingLdap" @click="testLdapConnection">
            测试连接
          </el-button>
          <el-button type="primary" :loading="savingGroup === group.key" @click="saveGroup(group)">保存 {{ group.title }}</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="samlDialog" :title="samlEditing ? '编辑 SAML IdP' : '新建 SAML IdP'" width="620px">
      <el-form :model="samlForm" label-width="130px">
        <el-form-item label="租户">
          <el-input v-model="samlForm.tenantCode" placeholder="default" :disabled="samlEditing" />
        </el-form-item>
        <el-form-item label="Registration ID">
          <el-input v-model="samlForm.registrationId" placeholder="default" :disabled="samlEditing" />
        </el-form-item>
        <el-form-item label="IdP Entity ID">
          <el-input v-model="samlForm.idpEntityId" placeholder="https://idp.example.com/entity" />
        </el-form-item>
        <el-form-item label="IdP SSO URL">
          <el-input v-model="samlForm.idpSsoUrl" placeholder="https://idp.example.com/sso" />
        </el-form-item>
        <el-form-item label="Metadata URL">
          <el-input v-model="samlForm.idpMetadataUrl" placeholder="https://idp.example.com/metadata" />
        </el-form-item>
        <el-form-item label="Metadata XML">
          <el-input v-model="samlForm.idpMetadataXml" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="SP Entity ID">
          <el-input v-model="samlForm.spEntityId" placeholder="urn:iam:sp" />
        </el-form-item>
        <el-form-item label="ACS Template">
          <el-input v-model="samlForm.acsTemplate" placeholder="http://localhost:8080/iam/login/saml2/sso/{registrationId}" />
        </el-form-item>
        <el-form-item label="属性映射">
          <el-input v-model="samlForm.attributeMapping" type="textarea" :rows="3" placeholder='{"email":"mail","displayName":"cn"}' />
          <p class="field-tip" style="font-size:.72rem;color:var(--text-muted);margin-top:4px">JSON：SAML 属性名 → IAM 字段名</p>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="samlForm.enabled" />
        </el-form-item>
        <el-form-item label="NameID 格式">
          <el-select v-model="samlForm.nameIdFormat" placeholder="选择 NameID 格式">
            <el-option label="Persistent" value="urn:oasis:names:tc:SAML:2.0:nameid-format:persistent" />
            <el-option label="Email" value="urn:oasis:names:tc:SAML:2.0:nameid-format:emailAddress" />
            <el-option label="Transient" value="urn:oasis:names:tc:SAML:2.0:nameid-format:transient" />
            <el-option label="Unspecified" value="urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified" />
          </el-select>
        </el-form-item>
        <el-form-item label="SP 签名证书">
          <el-input v-model="samlForm.signingCertPem" type="textarea" :rows="4" placeholder="-----BEGIN CERTIFICATE-----" />
        </el-form-item>
        <el-form-item label="SP 加密证书">
          <el-input v-model="samlForm.encryptionCertPem" type="textarea" :rows="4" placeholder="-----BEGIN CERTIFICATE-----" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="samlDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSaml">保存</el-button>
      </template>
    </el-dialog>

    <!-- SCIM Token dialog -->
    <el-dialog v-model="scimTokenDialog" :title="scimTokenEditing ? '编辑 Token' : '创建 SCIM Token'" width="460px">
      <el-form :model="scimTokenForm" label-width="110px">
        <el-form-item label="名称">
          <el-input v-model="scimTokenForm.name" placeholder="Okta Provisioner" />
        </el-form-item>
        <el-form-item label="租户">
          <el-input v-model="scimTokenForm.tenantCode" placeholder="default" />
        </el-form-item>
        <el-form-item label="Scope">
          <el-input v-model="scimTokenForm.scope" placeholder="sp_manage" />
        </el-form-item>
        <el-form-item label="有效期（天）">
          <el-input-number v-model="scimTokenForm.ttlDays" :min="1" :max="3650" />
        </el-form-item>
        <div v-if="scimTokenRaw" class="raw-token-box">
          <div class="raw-token-label">Token（仅显示一次，请妥善保管）</div>
          <code class="raw-token-value">{{ scimTokenRaw }}</code>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="scimTokenDialog = false">关闭</el-button>
        <el-button v-if="!scimTokenRaw" type="primary" :loading="savingScimToken" @click="saveScimToken">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, http, type ConfigItem, type SamlIdpRow, type ScimTokenRow } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'

type ConfigField = { key: string; label: string; type: 'string' | 'secret' | 'int' | 'boolean'; required?: boolean; placeholder?: string }
type ConfigGroup = { key: string; title: string; icon: string; desc: string; fields: ConfigField[] }

const activeTab = ref('saml')
const ldapPreset = ref('')
const configItems = ref<ConfigItem[]>([])
const drafts = ref<Record<string, string>>({})
const savingGroup = ref('')
const testingLdap = ref(false)

const scimBaseUrl = computed(() => `${location.origin}/iam/scim/v2`)
const scimTokens = ref<ScimTokenRow[]>([])
const scimTokenLoading = ref(false)
const scimTokenDialog = ref(false)
const scimTokenEditing = ref(false)
const savingScimToken = ref(false)
const scimTokenRaw = ref('')
const scimTokenForm = reactive({ name: '', tenantCode: 'default', scope: '', ttlDays: 365 })
const scimDebugMethod = ref('GET')
const scimDebugPath = ref('/scim/v2/Users')
const scimDebugToken = ref('')
const scimDebugBody = ref('')
const scimDebugLoading = ref(false)
const scimDebugResult = ref('')
const scimDebugStatus = ref('')

const samlRows = ref<SamlIdpRow[]>([])
const samlLoading = ref(false)
const samlDialog = ref(false)
const samlEditing = ref(false)
const samlForm = reactive<SamlIdpRow>({
  tenantCode: 'default',
  registrationId: 'default',
  idpEntityId: '',
  idpSsoUrl: '',
  idpMetadataUrl: '',
  idpMetadataXml: '',
  spEntityId: 'urn:iam:sp',
  acsTemplate: '',
  enabled: true,
  signingCertPem: '',
  encryptionCertPem: '',
  nameIdFormat: 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent',
  attributeMapping: '{"email":"mail","displayName":"cn"}',
})

const configGroups: ConfigGroup[] = [
  {
    key: 'cas',
    title: 'CAS',
    desc: '企业单点登录服务端配置',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>',
    fields: [
      { key: 'iam.cas.server-url', label: 'CAS Server URL', type: 'string', required: true, placeholder: 'https://cas.example.com/cas' },
      { key: 'iam.cas.service-url', label: 'Service URL', type: 'string', required: true, placeholder: 'http://localhost:8080/iam/api/auth/cas/callback' }
    ]
  },
  {
    key: 'ldap',
    title: 'LDAP',
    desc: '目录服务与企业账号源',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3v18"/><path d="M5 8h14"/><path d="M5 16h14"/></svg>',
    fields: [
      { key: 'iam.ldap.url', label: 'LDAP URL', type: 'string', required: true, placeholder: 'ldap://ldap.example.com:389' },
      { key: 'iam.ldap.base', label: 'Base DN', type: 'string', required: true, placeholder: 'dc=example,dc=com' },
      { key: 'iam.ldap.user-dn-pattern', label: 'User DN Pattern', type: 'string', placeholder: 'uid={0},ou=people' },
      { key: 'iam.ldap.user-search-filter', label: 'User Search Filter', type: 'string', placeholder: '(uid={0})' },
      { key: 'iam.ldap.manager-dn', label: 'Manager DN', type: 'string' },
      { key: 'iam.ldap.manager-password', label: 'Manager Password', type: 'secret' },
      { key: 'iam.ldap.attribute-mapping', label: '属性映射', type: 'string', placeholder: 'mail=email,cn=displayName' }
    ]
  },
  {
    key: 'social',
    title: '社交登录',
    desc: '微信、QQ、支付宝、钉钉与企微',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/></svg>',
    fields: [
      { key: 'iam.social.redirect-base-url', label: 'Redirect Base URL', type: 'string', required: true },
      { key: 'iam.social.wechat.app-id', label: '微信 App ID', type: 'string' },
      { key: 'iam.social.wechat.app-secret', label: '微信 App Secret', type: 'secret' },
      { key: 'iam.social.qq.app-id', label: 'QQ App ID', type: 'string' },
      { key: 'iam.social.qq.app-key', label: 'QQ App Key', type: 'secret' },
      { key: 'iam.social.alipay.app-id', label: '支付宝 App ID', type: 'string' },
      { key: 'iam.social.alipay.app-private-key', label: '支付宝私钥', type: 'secret' },
      { key: 'iam.social.alipay.alipay-public-key', label: '支付宝公钥', type: 'secret' },
      { key: 'iam.social.alipay.redirect-uri', label: '支付宝 Redirect URI', type: 'string' },
      { key: 'iam.social.dingtalk.app-id', label: '钉钉 App ID', type: 'string' },
      { key: 'iam.social.dingtalk.app-secret', label: '钉钉 App Secret', type: 'secret' },
      { key: 'iam.social.wecom.corp-id', label: '企微 Corp ID', type: 'string' },
      { key: 'iam.social.wecom.agent-id', label: '企微 Agent ID', type: 'string' },
      { key: 'iam.social.wecom.corp-secret', label: '企微 Corp Secret', type: 'secret' }
    ]
  },
  {
    key: 'sms',
    title: '短信登录',
    desc: '验证码登录与短信服务商',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/></svg>',
    fields: [
      { key: 'iam.sms.provider', label: 'Provider', type: 'string', required: true, placeholder: 'stub / aliyun' },
      { key: 'iam.sms.code-ttl-seconds', label: '验证码 TTL', type: 'int', placeholder: '300' },
      { key: 'iam.sms.code-length', label: '验证码长度', type: 'int', placeholder: '6' },
      { key: 'iam.sms.aliyun-access-key', label: '阿里云 Access Key', type: 'secret' },
      { key: 'iam.sms.aliyun-secret', label: '阿里云 Secret', type: 'secret' },
      { key: 'iam.sms.aliyun-sign-name', label: '短信签名', type: 'string' },
      { key: 'iam.sms.aliyun-template-code', label: '模板 Code', type: 'string' },
      { key: 'iam.sms.aliyun-endpoint', label: 'Endpoint', type: 'string', placeholder: 'dysmsapi.aliyuncs.com' }
    ]
  },
  {
    key: 'magic',
    title: 'Magic Link',
    desc: '邮件免密登录',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg>',
    fields: [
      { key: 'iam.magic-link.provider', label: 'Provider', type: 'string', required: true, placeholder: 'stub / smtp' },
      { key: 'iam.magic-link.base-url', label: 'Base URL', type: 'string', required: true, placeholder: 'http://localhost:5173/magic-callback' },
      { key: 'iam.magic-link.ttl-minutes', label: '有效期(分钟)', type: 'int', placeholder: '15' },
      { key: 'iam.magic-link.smtp-host', label: 'SMTP Host', type: 'string' },
      { key: 'iam.magic-link.smtp-port', label: 'SMTP Port', type: 'int', placeholder: '587' },
      { key: 'iam.magic-link.smtp-username', label: 'SMTP Username', type: 'string' },
      { key: 'iam.magic-link.smtp-password', label: 'SMTP Password', type: 'secret' },
      { key: 'iam.magic-link.smtp-from', label: 'From', type: 'string' },
      { key: 'iam.magic-link.smtp-starttls', label: 'STARTTLS', type: 'boolean', placeholder: 'true' }
    ]
  }
]

const overviewCards = computed(() => {
  const samlReady = samlRows.value.some(row => row.enabled && (row.idpMetadataUrl || row.idpSsoUrl))
  return [
    { key: 'saml', title: 'SAML 2.0', desc: '企业 IdP 联邦登录', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/></svg>', ready: samlReady, percent: samlReady ? 100 : 0 },
    ...configGroups.map(group => {
      const required = group.fields.filter(field => field.required)
      const done = required.filter(field => Boolean((drafts.value[field.key] || '').trim())).length
      const percent = required.length ? Math.round(done / required.length * 100) : 0
      return { key: group.key, title: group.title, desc: group.desc, icon: group.icon, ready: percent === 100, percent }
    })
  ]
})

async function loadConfig(): Promise<void> {
  const res = await adminApi.config()
  configItems.value = res.items || []
  const next: Record<string, string> = {}
  for (const item of configItems.value) next[item.key] = item.value || ''
  for (const group of configGroups) {
    for (const field of group.fields) {
      if (!(field.key in next)) next[field.key] = ''
    }
  }
  drafts.value = next
}

async function loadSaml(): Promise<void> {
  samlLoading.value = true
  try {
    samlRows.value = await adminApi.listSamlIdps()
  } finally {
    samlLoading.value = false
  }
}

async function loadScimTokens(): Promise<void> {
  scimTokenLoading.value = true
  try {
    scimTokens.value = await adminApi.listScimTokens()
  } finally {
    scimTokenLoading.value = false
  }
}

function editScimToken(_row: ScimTokenRow | null): void {
  scimTokenEditing.value = true
  scimTokenRaw.value = ''
  scimTokenForm.name = ''
  scimTokenForm.tenantCode = 'default'
  scimTokenForm.scope = ''
  scimTokenForm.ttlDays = 365
  scimTokenDialog.value = true
}

async function saveScimToken(): Promise<void> {
  savingScimToken.value = true
  try {
    const res = await adminApi.createScimToken({
      name: scimTokenForm.name,
      tenantCode: scimTokenForm.tenantCode,
      scope: scimTokenForm.scope,
      ttlDays: scimTokenForm.ttlDays,
    })
    scimTokenRaw.value = (res as any).token || ''
    savingScimToken.value = false
    ElMessage.success('Token 已创建（请复制保存）')
    await loadScimTokens()
  } catch (e) {
    savingScimToken.value = false
    throw e
  }
}

async function sendScimDebug(): Promise<void> {
  scimDebugLoading.value = true
  scimDebugResult.value = ''
  scimDebugStatus.value = ''
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/scim+json' }
    if (scimDebugToken.value) headers['Authorization'] = 'Bearer ' + scimDebugToken.value
    let body: string | undefined
    if (scimDebugMethod.value !== 'GET' && scimDebugBody.value) body = scimDebugBody.value
    const base = http.defaults.baseURL
    http.defaults.baseURL = '/iam'
    const res = await http.request({
      url: scimDebugPath.value,
      method: scimDebugMethod.value as any,
      headers,
      data: body,
    })
    http.defaults.baseURL = base
    scimDebugStatus.value = String((res as any).status)
    scimDebugResult.value = JSON.stringify((res as any).data, null, 2)
  } catch (e: any) {
    const base = http.defaults.baseURL
    http.defaults.baseURL = '/iam'
    scimDebugStatus.value = String(e?.response?.status || 'ERR')
    scimDebugResult.value = JSON.stringify(e?.response?.data || e?.message || '请求失败', null, 2)
    http.defaults.baseURL = base
  } finally {
    scimDebugLoading.value = false
  }
}

async function revokeScimToken(row: ScimTokenRow): Promise<void> {
  await ElMessageBox.confirm(`吊销 SCIM Token "${row.name}"?`, '确认', { type: 'warning' })
  await adminApi.revokeScimToken(row.id)
  ElMessage.success('已吊销')
  await loadScimTokens()
}

function applyLdapPreset(preset: string): void {
  const presets: Record<string, { 'iam.ldap.user-dn-pattern': string; 'iam.ldap.user-search-filter': string }> = {
    'generic': { 'iam.ldap.user-dn-pattern': 'uid={0},ou=people', 'iam.ldap.user-search-filter': '(uid={0})' },
    'active-directory': { 'iam.ldap.user-dn-pattern': '{0}@corp', 'iam.ldap.user-search-filter': '(sAMAccountName={0})' },
    'active-directory-bind': { 'iam.ldap.user-dn-pattern': 'CN={0},OU=Users,DC=corp,DC=local', 'iam.ldap.user-search-filter': '(sAMAccountName={0})' },
  }
  const p = presets[preset]
  if (p) {
    drafts.value['iam.ldap.user-dn-pattern'] = p['iam.ldap.user-dn-pattern']
    drafts.value['iam.ldap.user-search-filter'] = p['iam.ldap.user-search-filter']
  }
}

async function saveGroup(group: ConfigGroup): Promise<void> {
  savingGroup.value = group.key
  try {
    for (const field of group.fields) {
      await adminApi.updateConfig({
        key: field.key,
        value: drafts.value[field.key] || '',
        type: field.type,
        description: field.label
      })
    }
    ElMessage.success('已保存')
    await loadConfig()
  } finally {
    savingGroup.value = ''
  }
}

async function testLdapConnection(): Promise<void> {
  testingLdap.value = true
  try {
    const res = await http.post('/admin/api/ldap/test', {
      url: drafts.value['iam.ldap.url'] || '',
      base: drafts.value['iam.ldap.base'] || '',
      managerDn: drafts.value['iam.ldap.manager-dn'] || '',
      managerPassword: drafts.value['iam.ldap.manager-password'] || '',
      useSsl: false
    })
    const data = res.data?.data || res.data
    if (data?.success) {
      ElMessage.success(data.message || '连接成功')
    } else {
      ElMessage.error(data.message || '连接失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '连接测试失败')
  } finally {
    testingLdap.value = false
  }
}

function resetSaml(row?: SamlIdpRow | null): void {
  Object.assign(samlForm, {
    tenantCode: row?.tenantCode || 'default',
    registrationId: row?.registrationId || 'default',
    idpEntityId: row?.idpEntityId || '',
    idpSsoUrl: row?.idpSsoUrl || '',
    idpMetadataUrl: row?.idpMetadataUrl || '',
    idpMetadataXml: row?.idpMetadataXml || '',
    spEntityId: row?.spEntityId || 'urn:iam:sp',
    acsTemplate: row?.acsTemplate || '',
    enabled: row?.enabled ?? true,
    signingCertPem: row?.signingCertPem || '',
    encryptionCertPem: row?.encryptionCertPem || '',
    nameIdFormat: row?.nameIdFormat || 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent',
    attributeMapping: row?.attributeMapping || '{"email":"mail","displayName":"cn"}',
  })
}

function downloadMetadata(row: SamlIdpRow): void {
  window.open('/iam/saml2/metadata/' + row.registrationId, '_blank')
}

function editSaml(row: SamlIdpRow | null): void {
  samlEditing.value = Boolean(row)
  resetSaml(row)
  samlDialog.value = true
}

async function saveSaml(): Promise<void> {
  if (!samlForm.tenantCode || !samlForm.registrationId || !samlForm.idpEntityId) {
    ElMessage.warning('租户、Registration ID、IdP Entity ID 必填')
    return
  }
  await adminApi.upsertSamlIdp({ ...samlForm })
  ElMessage.success('已保存')
  samlDialog.value = false
  await loadSaml()
}

async function deleteSaml(row: SamlIdpRow): Promise<void> {
  await ElMessageBox.confirm(`删除 SAML IdP ${row.registrationId}?`, '确认', { type: 'warning' })
  await adminApi.deleteSamlIdp(row.tenantCode, row.registrationId)
  ElMessage.success('已删除')
  await loadSaml()
}

onMounted(async () => {
  await Promise.all([loadConfig(), loadSaml()])
})

watch(activeTab, (tab) => {
  if (tab === 'scim') loadScimTokens()
})
</script>

<style scoped>
.auth-protocols { display: flex; flex-direction: column; gap: 18px; }
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
}
.protocol-card {
  min-height: 132px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-card);
}
.card-top { display: flex; align-items: center; justify-content: space-between; }
.card-icon {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 8px;
}
.card-icon :deep(svg) { width: 18px; height: 18px; }
.protocol-card h3 { margin: 14px 0 4px; font-size: 0.98rem; color: var(--text-primary); }
.protocol-card p { min-height: 36px; margin: 0; font-size: 0.78rem; color: var(--text-muted); line-height: 1.45; }
.progress-line { height: 4px; margin-top: 14px; border-radius: 999px; background: var(--bg-tertiary); overflow: hidden; }
.progress-line span { display: block; height: 100%; background: var(--accent); transition: width var(--dur-normal) var(--ease-out); }
.protocol-tabs { padding: 16px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-card); }
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}
.config-field { min-width: 0; }
.field-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-secondary);
}
.required-dot { width: 6px; height: 6px; border-radius: 999px; background: var(--danger); }
.actions-row { display: flex; justify-content: flex-end; margin-top: 16px; }
.scim-debug { display: flex; flex-direction: column; gap: 10px; }
.debug-row { display: flex; gap: 8px; align-items: center; }
.debug-result { background: var(--bg-tertiary); border-radius: 6px; padding: 10px; }
.debug-result pre { margin: 0; font-size: .78rem; white-space: pre-wrap; word-break: break-all; }
.debug-status { font-size: .78rem; color: var(--text-muted); margin-bottom: 6px; }
</style>
