<template>
  <el-container style="min-height:100vh">
    <el-header>
      <div class="title">IAM 控制台</div>
      <el-dropdown @command="onCmd">
        <span class="user">{{ profile?.username }} <el-icon><ArrowDown /></el-icon></span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="admin" v-if="isAdmin">管理后台</el-dropdown-item>
            <el-dropdown-item command="setupMfa">启用 MFA</el-dropdown-item>
            <el-dropdown-item command="logout">登出</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>
    <el-main>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-card header="用户信息">
            <p><b>用户名：</b>{{ profile?.username }}</p>
            <p><b>邮箱：</b>{{ profile?.email }}</p>
            <p><b>手机：</b>{{ profile?.phone }}</p>
            <p><b>租户：</b>{{ profile?.tenant }}</p>
            <p><b>MFA：</b>{{ profile?.mfaEnabled ? '已启用' : '未启用' }}</p>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card header="角色">
            <el-tag v-for="r in profile?.roles" :key="r" style="margin:4px">{{ r }}</el-tag>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card header="权限">
            <el-tag v-for="p in permissions" :key="p" type="info" style="margin:2px">{{ p }}</el-tag>
          </el-card>
        </el-col>
      </el-row>
    </el-main>

    <el-dialog v-model="mfaDialog.visible" title="启用 MFA (TOTP)" width="420px">
      <div v-if="mfaDialog.uri">
        <p>1. 在 Authenticator 中扫描或手动添加：</p>
        <pre class="uri">{{ mfaDialog.uri }}</pre>
        <p>2. 输入 6 位动态码确认：</p>
        <el-input v-model="mfaDialog.code" maxlength="6" />
      </div>
      <template #footer>
        <el-button @click="mfaDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmMfa">确认</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { api, hasRole } from '../api'

const profile = ref<any>(null)
const permissions = ref<string[]>([])
const router = useRouter()
const mfaDialog = ref({ visible: false, uri: '', code: '' })
const isAdmin = computed(() => hasRole('ROLE_ADMIN'))

onMounted(async () => {
  profile.value = await api.me()
  const token = localStorage.getItem('access_token') || ''
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    permissions.value = payload.perms || []
  } catch { permissions.value = [] }
})

async function onCmd(cmd: string) {
  if (cmd === 'logout') {
    await api.logout()
    router.push('/login')
  } else if (cmd === 'setupMfa') {
    const r = await api.setupMfa()
    mfaDialog.value = { visible: true, uri: r.otpauth, code: '' }
  } else if (cmd === 'admin') {
    router.push('/admin')
  }
}

async function confirmMfa() {
  try {
    await api.confirmMfa(mfaDialog.value.code)
    ElMessage.success('MFA 已启用')
    mfaDialog.value.visible = false
    profile.value = await api.me()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '确认失败')
  }
}
</script>

<style scoped>
.el-header { background: #fff; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #eee; }
.title { font-weight: 600; }
.user { cursor: pointer; }
.uri { white-space: pre-wrap; word-break: break-all; background: #f5f5f5; padding: 8px; font-size: 12px; }
</style>
