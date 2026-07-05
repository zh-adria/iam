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
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span :class="['neo-tag', row.enabled ? 'success' : 'dim']">{{ row.enabled ? '启用' : '停用' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="right">
            <template #default="{ row }">
              <el-button size="small" plain type="primary" @click="editSaml(row)">编辑</el-button>
              <el-button size="small" plain type="danger" @click="deleteSaml(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-for="group in configGroups" :key="group.key" :label="group.title" :name="group.key">
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
        <el-form-item label="启用">
          <el-switch v-model="samlForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="samlDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSaml">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type ConfigItem, type SamlIdpRow } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'

type ConfigField = { key: string; label: string; type: 'string' | 'secret' | 'int' | 'boolean'; required?: boolean; placeholder?: string }
type ConfigGroup = { key: string; title: string; icon: string; desc: string; fields: ConfigField[] }

const activeTab = ref('saml')
const configItems = ref<ConfigItem[]>([])
const drafts = ref<Record<string, string>>({})
const savingGroup = ref('')

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
  enabled: true
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
      { key: 'iam.ldap.manager-dn', label: 'Manager DN', type: 'string' },
      { key: 'iam.ldap.manager-password', label: 'Manager Password', type: 'secret' }
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
    enabled: row?.enabled ?? true
  })
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
</style>
