<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>IAM 统一身份认证</h2>
      <el-tabs v-model="tab">
        <el-tab-pane label="密码登录" name="password">
          <el-form @submit.prevent="onLogin" label-width="80px">
            <el-form-item label="租户"><el-input v-model="form.tenantCode" placeholder="default" /></el-form-item>
            <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
            <el-button type="primary" :loading="loading" @click="onLogin" style="width:100%">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="短信验证码" name="sms">
          <el-form label-width="80px">
            <el-form-item label="手机号"><el-input v-model="smsForm.phone" /></el-form-item>
            <el-form-item label="验证码">
              <div style="display:flex;gap:8px;width:100%">
                <el-input v-model="smsForm.code" />
                <el-button :disabled="smsCountdown > 0" @click="onSmsSend">{{ smsCountdown > 0 ? `${smsCountdown}s` : '发送' }}</el-button>
              </div>
            </el-form-item>
            <el-button type="primary" :loading="loading" @click="onSmsLogin" style="width:100%">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="Magic Link" name="magic">
          <el-form label-width="80px">
            <el-form-item label="邮箱"><el-input v-model="magicEmail" /></el-form-item>
            <el-button type="primary" :loading="loading" @click="onMagicSend">发送登录链接</el-button>
            <p class="hint">链接发送到邮箱，点击后自动登录（stub 模式下链接打印在后端控制台）。</p>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="社交登录" name="social">
          <el-space wrap>
            <el-button @click="social('wechat')">微信</el-button>
            <el-button @click="social('alipay')">支付宝</el-button>
            <el-button @click="social('qq')">QQ</el-button>
            <el-button @click="social('dingtalk')">钉钉</el-button>
            <el-button @click="social('wecom')">企业微信</el-button>
          </el-space>
          <p class="hint">需在 application.yml 配置对应 appId/appSecret。</p>
        </el-tab-pane>

        <el-tab-pane label="企业 SSO" name="sso">
          <el-space wrap>
            <el-button @click="casLogin">CAS SSO</el-button>
            <el-button @click="samlLogin">SAML 2.0</el-button>
          </el-space>
          <p class="hint">需配置 iam.cas.server-url / iam.saml.idp.*。</p>
        </el-tab-pane>

        <el-tab-pane label="OAuth2" name="oauth2">
          <p class="hint">演示客户端：<code>demo-client</code> / <code>demo-secret</code></p>
          <el-button type="success" @click="oauth2Authorize">跳转授权</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { api, saveSession } from '../api'

const tab = ref('password')
const loading = ref(false)
const router = useRouter()
const form = ref({ tenantCode: 'default', username: '', password: '' })
const smsForm = ref({ phone: '', code: '' })
const magicEmail = ref('')
const smsCountdown = ref(0)

async function onLogin(): Promise<void> {
  loading.value = true
  try {
    const r = await api.login(form.value)
    if (r.mfaRequired) {
      sessionStorage.setItem('mfa_token', r.mfaToken || '')
      router.push('/mfa')
      return
    }
    saveSession(r)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function onSmsSend(): Promise<void> {
  if (!smsForm.value.phone) { ElMessage.warning('请输入手机号'); return }
  await api.smsSend(smsForm.value.phone)
  ElMessage.success('验证码已发送')
  smsCountdown.value = 60
  const t = setInterval(() => {
    smsCountdown.value--
    if (smsCountdown.value <= 0) clearInterval(t)
  }, 1000)
}

async function onSmsLogin(): Promise<void> {
  loading.value = true
  try {
    const r = await api.smsLogin(smsForm.value.phone, smsForm.value.code)
    saveSession(r)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '登录失败')
  } finally { loading.value = false }
}

async function onMagicSend(): Promise<void> {
  if (!magicEmail.value) { ElMessage.warning('请输入邮箱'); return }
  await api.magicSend(magicEmail.value)
  ElMessage.success('登录链接已发送到邮箱')
}

async function social(provider: string): Promise<void> {
  try {
    const url = await api.socialAuthorize(provider)
    location.href = url
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '未配置')
  }
}

async function casLogin(): Promise<void> {
  try {
    const url = await api.casAuthorize()
    location.href = url
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || 'CAS 未配置')
  }
}

function samlLogin(): void {
  location.href = '/iam/saml2/authenticate/default'
}

function oauth2Authorize(): void {
  const url = `/iam/oauth/authorize?response_type=code&client_id=demo-client&redirect_uri=${encodeURIComponent('http://localhost:5173/callback')}&scope=openid`
  location.href = url
}
</script>

<style scoped>
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f0f2f5; }
.login-card { width: 460px; }
.hint { color: #909399; font-size: 12px; margin-top: 8px; }
</style>
