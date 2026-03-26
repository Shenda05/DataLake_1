<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isAuthExpiredError } from '../api/client';
import { createUser, deleteUser, listRoles, listUsers, updateUser, type RoleSummary, type UserSummary } from '../api/platform';

const users = ref<UserSummary[]>([]);
const roles = ref<RoleSummary[]>([]);
const loading = ref(false);
const editingUserId = ref<number | null>(null);
const form = reactive({
  username: '',
  password: '',
  roleId: undefined as number | undefined,
  status: 'ENABLED'
});

const enabledCount = computed(() => users.value.filter((item) => item.status === 'ENABLED').length);
const disabledCount = computed(() => users.value.filter((item) => item.status === 'DISABLED').length);

async function loadData() {
  const [userItems, roleItems] = await Promise.all([listUsers(), listRoles()]);
  users.value = userItems;
  roles.value = roleItems;
  if (!form.roleId && roles.value.length > 0) {
    form.roleId = roles.value[0].roleId;
  }
}

function resetForm() {
  editingUserId.value = null;
  form.username = '';
  form.password = '';
  form.roleId = roles.value[0]?.roleId;
  form.status = 'ENABLED';
}

function loadUser(user: UserSummary) {
  editingUserId.value = user.userId;
  form.username = user.username;
  form.password = '';
  form.roleId = user.roleId;
  form.status = user.status;
}

async function submit() {
  if (!form.username || !form.roleId || (!editingUserId.value && !form.password)) {
    ElMessage.warning('请填写用户名、角色，并在新增用户时输入密码');
    return;
  }
  loading.value = true;
  try {
    const payload = {
      username: form.username,
      password: form.password || undefined,
      roleId: form.roleId,
      status: form.status
    };
    if (editingUserId.value) {
      await updateUser(editingUserId.value, payload);
      ElMessage.success('用户信息已更新');
    } else {
      await createUser({ ...payload, password: form.password });
      ElMessage.success('用户创建成功');
    }
    resetForm();
    await loadData();
  } catch (error) {
    ElMessage.error(`用户保存失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function handleDelete(user: UserSummary) {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${user.username} 吗？`, '删除确认', {
      type: 'warning'
    });
    await deleteUser(user.userId);
    ElMessage.success('用户已删除');
    if (editingUserId.value === user.userId) {
      resetForm();
    }
    await loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败: ${(error as Error).message}`);
    }
  }
}

onMounted(async () => {
  try {
    await loadData();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
    ElMessage.error(`用户页初始化失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid">
    <section class="stat-grid">
      <el-card shadow="hover">
        <p class="stat-label">用户总数</p>
        <p class="stat-value">{{ users.length }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">启用中</p>
        <p class="stat-value">{{ enabledCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">停用中</p>
        <p class="stat-value">{{ disabledCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">角色数</p>
        <p class="stat-value">{{ roles.length }}</p>
      </el-card>
    </section>

    <div class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ editingUserId ? '编辑用户' : '新增用户' }}</span>
            <div class="card-header-actions">
              <el-tag type="success">Admin API</el-tag>
              <el-button link type="primary" @click="resetForm">重置表单</el-button>
            </div>
          </div>
        </template>
        <el-form label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item :label="editingUserId ? '重置密码（留空则不修改）' : '登录密码'">
            <el-input v-model="form.password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.roleId" placeholder="请选择角色">
              <el-option
                v-for="role in roles"
                :key="role.roleId"
                :label="`${role.roleName} / ${role.roleDesc || ''}`"
                :value="role.roleId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">ENABLED</el-radio-button>
              <el-radio-button label="DISABLED">DISABLED</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">{{ editingUserId ? '保存修改' : '创建用户' }}</el-button>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>用户与权限</span>
            <el-button link type="primary" @click="loadData">刷新</el-button>
          </div>
        </template>
        <el-table :data="users" stripe>
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="role" label="角色" width="140" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="createTime" label="创建时间" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="loadUser(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>
