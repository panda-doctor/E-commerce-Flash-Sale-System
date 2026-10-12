<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/apiClient'
import type { UserProfile } from '../types/api'

const users = ref<UserProfile[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const statusFilter = ref<number | undefined>()
const dialogVisible = ref(false)
const saving = ref(false)
const editingUser = ref<UserProfile | null>(null)
const formError = ref('')
const form = reactive({ accountName: '', displayName: '', password: '', status: 1 })

onMounted(() => { void loadUsers() })

async function loadUsers() {
  loading.value = true
  try {
    const result = await api.listUsers({ page: page.value, pageSize: pageSize.value, keyword: keyword.value.trim(), status: statusFilter.value })
    users.value = result.records
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户列表加载失败。')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingUser.value = null
  Object.assign(form, { accountName: '', displayName: '', password: '', status: 1 })
  formError.value = ''
  dialogVisible.value = true
}

function openEdit(user: UserProfile) {
  editingUser.value = user
  Object.assign(form, { accountName: user.accountName, displayName: user.displayName ?? '', password: '', status: user.status })
  formError.value = ''
  dialogVisible.value = true
}

function validate(): boolean {
  formError.value = ''
  if (!editingUser.value && !/^[a-z0-9._-]{3,32}$/i.test(form.accountName.trim())) {
    formError.value = '账号名须为 3 至 32 位字母、数字或 . _ -。'
    return false
  }
  if (form.displayName.trim().length > 64) {
    formError.value = '昵称不能超过 64 个字符。'
    return false
  }
  if ((!editingUser.value || form.password) && (form.password.length < 10 || new TextEncoder().encode(form.password).length > 72)) {
    formError.value = '密码至少 10 个字符，UTF-8 长度最多 72 字节。'
    return false
  }
  return true
}

async function saveUser() {
  if (!validate() || saving.value) return
  saving.value = true
  try {
    if (editingUser.value) {
      await api.updateUser(editingUser.value.id, {
        displayName: form.displayName.trim(),
        status: form.status,
        ...(form.password ? { password: form.password } : {}),
      })
      ElMessage.success(form.password ? '用户资料已更新，原登录会话已撤销。' : '用户资料已更新。')
    } else {
      await api.createUser({ accountName: form.accountName.trim(), displayName: form.displayName.trim(), password: form.password })
      ElMessage.success('用户已创建。')
    }
    dialogVisible.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败，请检查输入后重试。')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user: UserProfile) {
  const nextStatus = user.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(nextStatus === 0 ? '禁用后该用户将不能登录，现有会话也会立即撤销。' : '启用后该用户可以重新登录。',
      nextStatus === 0 ? '确认禁用用户' : '确认启用用户', { confirmButtonText: nextStatus === 0 ? '禁用用户' : '启用用户', cancelButtonText: '取消', type: 'warning' })
    await api.updateUser(user.id, { status: nextStatus })
    ElMessage.success(nextStatus === 0 ? '用户已禁用。' : '用户已启用。')
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败。')
  }
}

async function removeUser(user: UserProfile) {
  try {
    await ElMessageBox.confirm(`将软删除账号“${user.accountName}”。原有订单会保留，用户登录会立即失效。`, '确认软删除用户', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning',
    })
    await api.deleteUser(user.id)
    ElMessage.success('用户已软删除。')
    if (users.value.length === 1 && page.value > 1) page.value -= 1
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败。')
  }
}

function createdAt(value: string): string {
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="page-view">
    <div class="page-heading"><div><div class="eyebrow">账号资料</div><h1>用户管理</h1><p>管理注册账号、启用状态和密码，删除用户时保留原订单记录。</p></div><el-button type="primary" @click="openCreate">＋ 创建用户</el-button></div>
    <section class="surface-card user-list-card">
      <div class="user-toolbar"><el-input v-model="keyword" clearable placeholder="搜索账号名或昵称" class="user-search" @keyup.enter="page = 1; loadUsers()" /><el-select v-model="statusFilter" clearable placeholder="全部状态" class="user-status-filter" @change="page = 1; loadUsers()"><el-option label="正常" :value="1" /><el-option label="已禁用" :value="0" /></el-select><el-button @click="page = 1; loadUsers()">查询</el-button><span class="user-total">共 {{ total }} 个账号</span></div>
      <el-table :data="users" v-loading="loading" row-key="id" class="user-table" empty-text="暂无符合条件的用户">
        <el-table-column label="用户" min-width="230"><template #default="scope"><div class="user-cell"><el-avatar :size="34">{{ (scope.row.displayName || scope.row.accountName).slice(0, 1).toUpperCase() }}</el-avatar><div><strong>{{ scope.row.displayName || '未设置昵称' }}</strong><small>{{ scope.row.accountName }}</small></div></div></template></el-table-column>
        <el-table-column prop="id" label="用户编号" min-width="145" />
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'" effect="light" round>{{ scope.row.status === 1 ? '正常' : '已禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="注册时间" min-width="175"><template #default="scope">{{ createdAt(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="195" fixed="right"><template #default="scope"><div class="user-row-actions"><el-button link type="primary" @click="openEdit(scope.row as UserProfile)">编辑</el-button><el-button link :type="scope.row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(scope.row as UserProfile)">{{ scope.row.status === 1 ? '禁用' : '启用' }}</el-button><el-button link type="danger" @click="removeUser(scope.row as UserProfile)">删除</el-button></div></template></el-table-column>
      </el-table>
      <div class="user-pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="loadUsers" @size-change="page = 1; loadUsers()" /></div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingUser ? '编辑用户' : '创建用户'" width="min(520px, calc(100vw - 32px))" destroy-on-close>
      <div class="user-dialog-form">
        <label v-if="!editingUser">账号名</label><el-input v-if="!editingUser" v-model="form.accountName" maxlength="32" autocomplete="off" placeholder="3 至 32 位字母、数字或 . _ -" />
        <label v-if="editingUser">账号名</label><el-input v-if="editingUser" :model-value="form.accountName" disabled />
        <label>昵称</label><el-input v-model="form.displayName" maxlength="64" autocomplete="off" placeholder="选填，最多 64 个字符" />
        <label>{{ editingUser ? '重置密码（选填）' : '初始密码' }}</label><el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="至少 10 个字符" />
        <template v-if="editingUser"><label>账号状态</label><el-select v-model="form.status"><el-option label="正常" :value="1" /><el-option label="禁用" :value="0" /></el-select></template>
        <p v-if="editingUser && form.password" class="user-password-note">保存后会撤销该账号全部登录会话。</p>
        <p v-if="formError" class="user-form-error">{{ formError }}</p>
      </div>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveUser">保存</el-button></template>
    </el-dialog>
  </div>
</template>
