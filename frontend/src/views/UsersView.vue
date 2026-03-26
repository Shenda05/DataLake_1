<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isAuthExpiredError } from '../api/client';
import {
  createUser,
  deleteUser,
  listRoles,
  listUsers,
  updateRole,
  updateUser,
  type RoleSummary,
  type UserSummary
} from '../api/platform';

type MenuOption = {
  key: string;
  label: string;
  adminOnly?: boolean;
};

const MENU_OPTIONS: MenuOption[] = [
  { key: 'dashboard', label: '首页' },
  { key: 'data-sources', label: '数据源管理' },
  { key: 'imports', label: '数据接入' },
  { key: 'datasets', label: '数据集管理' },
  { key: 'queries', label: '查询分析' },
  { key: 'governance', label: '数据治理' },
  { key: 'tasks', label: '任务调度' },
  { key: 'logs', label: '日志监控' },
  { key: 'users', label: '用户与权限', adminOnly: true }
];

const users = ref<UserSummary[]>([]);
const roles = ref<RoleSummary[]>([]);
const loading = ref(false);
const roleLoading = ref(false);
const editingUserId = ref<number | null>(null);
const selectedRoleId = ref<number | null>(null);
const form = reactive({
  username: '',
  password: '',
  roleId: undefined as number | undefined,
  status: 'ENABLED'
});
const roleForm = reactive({
  roleDesc: '',
  menus: [] as string[]
});

const enabledCount = computed(() => users.value.filter((item) => item.status === 'ENABLED').length);
const disabledCount = computed(() => users.value.filter((item) => item.status === 'DISABLED').length);
const selectedRole = computed(() => roles.value.find((item) => item.roleId === selectedRoleId.value) ?? null);
const selectedRoleIsAdmin = computed(() => selectedRole.value?.roleName === 'ADMIN');
const selectedRoleMenuLabels = computed(() =>
  MENU_OPTIONS.filter((option) => roleForm.menus.includes(option.key)).map((option) => option.label)
);

async function loadData() {
  const previousRoleId = selectedRoleId.value;
  const [userItems, roleItems] = await Promise.all([listUsers(), listRoles()]);
  users.value = userItems;
  roles.value = roleItems;
  if (!form.roleId && roles.value.length > 0) {
    form.roleId = roles.value[0].roleId;
  }
  const nextRole = roleItems.find((item) => item.roleId === previousRoleId) ?? roleItems[0] ?? null;
  applyRole(nextRole);
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

function applyRole(role: RoleSummary | null) {
  selectedRoleId.value = role?.roleId ?? null;
  roleForm.roleDesc = role?.roleDesc ?? '';
  roleForm.menus = normalizeMenus(role?.menuPermissions ?? [], role?.roleName);
}

function selectRole(role: RoleSummary) {
  applyRole(role);
}

function resetRoleForm() {
  applyRole(selectedRole.value);
}

function menuLabel(menuKey: string) {
  return MENU_OPTIONS.find((item) => item.key === menuKey)?.label ?? menuKey;
}

function normalizeMenus(menuKeys: string[], roleName?: string | null) {
  const allowedKeys = new Set(
    MENU_OPTIONS.filter((option) => roleName === 'ADMIN' || !option.adminOnly).map((option) => option.key)
  );
  return MENU_OPTIONS.map((option) => option.key).filter((key) => allowedKeys.has(key) && menuKeys.includes(key));
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

async function saveRole() {
  if (!selectedRole.value) {
    ElMessage.warning('请先选择一个角色');
    return;
  }
  if (!roleForm.menus.length) {
    ElMessage.warning('请至少保留一个菜单权限');
    return;
  }
  if (selectedRoleIsAdmin.value && !roleForm.menus.includes('users')) {
    ElMessage.warning('管理员角色必须保留“用户与权限”菜单');
    return;
  }
  roleLoading.value = true;
  try {
    await updateRole(selectedRole.value.roleId, {
      roleDesc: roleForm.roleDesc,
      menus: normalizeMenus(roleForm.menus, selectedRole.value.roleName)
    });
    ElMessage.success('角色菜单权限已更新，使用该角色重新登录后即可看到最新菜单');
    await loadData();
  } catch (error) {
    ElMessage.error(`角色权限保存失败: ${(error as Error).message}`);
  } finally {
    roleLoading.value = false;
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
            <span>用户列表</span>
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

    <div class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>系统角色</span>
            <span class="inline-tip">菜单权限已改为从数据库读取，修改后重新登录即可验证。</span>
          </div>
        </template>
        <el-table :data="roles" stripe>
          <el-table-column prop="roleName" label="角色标识" width="140" />
          <el-table-column prop="roleDesc" label="角色说明" width="180" />
          <el-table-column label="菜单权限">
            <template #default="{ row }">
              <div class="tag-cluster">
                <el-tag v-for="menuKey in row.menuPermissions" :key="`${row.roleId}-${menuKey}`" size="small" effect="plain">
                  {{ menuLabel(menuKey) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectRole(row)">配置</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>角色菜单权限</span>
            <div class="card-header-actions">
              <el-button link type="primary" :disabled="!selectedRole" @click="resetRoleForm">恢复当前配置</el-button>
            </div>
          </div>
        </template>
        <div v-if="selectedRole" class="page-grid">
          <el-alert
            type="info"
            :closable="false"
            :title="`当前角色：${selectedRole.roleName}。管理员角色必须保留“用户与权限”菜单，普通角色不会展示该入口。`"
          />
          <el-form label-position="top">
            <el-form-item label="角色标识">
              <el-input :model-value="selectedRole.roleName" disabled />
            </el-form-item>
            <el-form-item label="角色说明">
              <el-input v-model="roleForm.roleDesc" placeholder="请输入角色说明" />
            </el-form-item>
            <el-form-item label="菜单权限">
              <el-checkbox-group v-model="roleForm.menus" class="permission-grid">
                <el-checkbox
                  v-for="option in MENU_OPTIONS"
                  :key="option.key"
                  :label="option.key"
                  :disabled="Boolean(option.adminOnly && !selectedRoleIsAdmin)"
                  class="permission-item"
                >
                  {{ option.label }}
                  <span v-if="option.adminOnly" class="permission-hint">仅管理员</span>
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-form>
          <div class="inline-tip">当前已选择 {{ roleForm.menus.length }} 个菜单：{{ selectedRoleMenuLabels.join('、') || '未选择' }}</div>
          <div class="hero-actions">
            <el-button type="primary" :loading="roleLoading" @click="saveRole">保存角色权限</el-button>
            <el-button @click="resetRoleForm">取消修改</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无可配置角色" />
      </el-card>
    </div>
  </div>
</template>
