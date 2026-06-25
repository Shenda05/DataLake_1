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
import { useAuthStore } from '../stores/auth';

type MenuOption = {
  key: string;
  label: string;
  adminOnly?: boolean;
};

type ActionOption = {
  key: string;
  label: string;
  adminOnly?: boolean;
};

type PermissionGroup = {
  key: string;
  label: string;
  description: string;
  items: string[];
};

const MENU_OPTIONS: MenuOption[] = [
  { key: 'dashboard', label: '电商仪表盘' },
  { key: 'data-sources', label: '数据源管理' },
  { key: 'imports', label: '电商数据接入' },
  { key: 'datasets', label: '电商数据集' },
  { key: 'queries', label: '电商查询分析' },
  { key: 'governance', label: '电商数据治理' },
  { key: 'tasks', label: '电商任务调度' },
  { key: 'logs', label: '任务与日志' },
  { key: 'users', label: '用户与权限', adminOnly: true }
];

const MENU_GROUPS: PermissionGroup[] = [
  {
    key: 'core',
    label: '核心导航',
    description: '控制首页、数据集、查询和日志这些高频浏览入口。',
    items: ['dashboard', 'datasets', 'queries', 'logs']
  },
  {
    key: 'pipeline',
    label: '数据链路',
    description: '控制数据源、数据接入、治理和任务这些生产链路入口。',
    items: ['data-sources', 'imports', 'governance', 'tasks']
  },
  {
    key: 'admin',
    label: '系统管理',
    description: '仅管理员可见，用于用户与权限管理。',
    items: ['users']
  }
];

const ACTION_OPTIONS: ActionOption[] = [
  { key: 'source.manage', label: '数据源管理操作', adminOnly: true },
  { key: 'import.database', label: '数据库表导入', adminOnly: true },
  { key: 'dataset.delete', label: '数据集删除', adminOnly: true },
  { key: 'governance.manage', label: '治理流程保存' },
  { key: 'governance.execute', label: '治理执行' },
  { key: 'task.manage', label: '任务配置' },
  { key: 'task.trigger', label: '任务触发' },
  { key: 'dataset.export', label: '数据集导出' },
  { key: 'query.export', label: '查询结果导出' },
  { key: 'log.export', label: '日志导出' },
  { key: 'user.manage', label: '用户管理', adminOnly: true },
  { key: 'role.manage', label: '角色配置', adminOnly: true },
  { key: 'log.replay', label: '日志回放' }
];

const ACTION_GROUPS: PermissionGroup[] = [
  {
    key: 'governance',
    label: '治理与调度',
    description: '控制治理流程保存、执行和任务配置/触发。',
    items: ['governance.manage', 'governance.execute', 'task.manage', 'task.trigger']
  },
  {
    key: 'export',
    label: '导出与回放',
    description: '控制数据集导出、查询导出、日志导出和失败日志回放。',
    items: ['dataset.export', 'query.export', 'log.export', 'log.replay']
  },
  {
    key: 'admin',
    label: '平台管理',
    description: '控制数据源管理、数据库表导入、数据集删除和系统后台操作。',
    items: ['source.manage', 'import.database', 'dataset.delete', 'user.manage', 'role.manage']
  }
];

const authStore = useAuthStore();
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
  menus: [] as string[],
  actions: [] as string[]
});

const enabledCount = computed(() => users.value.filter((item) => item.status === 'ENABLED').length);
const disabledCount = computed(() => users.value.filter((item) => item.status === 'DISABLED').length);
const selectedRole = computed(() => roles.value.find((item) => item.roleId === selectedRoleId.value) ?? null);
const selectedRoleIsAdmin = computed(() => selectedRole.value?.roleName === 'ADMIN');
const canManageUsers = computed(() => authStore.hasAction('user.manage'));
const canManageRoles = computed(() => authStore.hasAction('role.manage'));
const selectedRoleMenuLabels = computed(() =>
  MENU_OPTIONS.filter((option) => roleForm.menus.includes(option.key)).map((option) => option.label)
);
const selectedRoleActionLabels = computed(() =>
  ACTION_OPTIONS.filter((option) => roleForm.actions.includes(option.key)).map((option) => option.label)
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
  roleForm.actions = normalizeActions(role?.actionPermissions ?? [], role?.roleName);
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

function actionLabel(actionKey: string) {
  return ACTION_OPTIONS.find((item) => item.key === actionKey)?.label ?? actionKey;
}

function normalizeMenus(menuKeys: string[], roleName?: string | null) {
  const allowedKeys = new Set(
    MENU_OPTIONS.filter((option) => roleName === 'ADMIN' || !option.adminOnly).map((option) => option.key)
  );
  return MENU_OPTIONS.map((option) => option.key).filter((key) => allowedKeys.has(key) && menuKeys.includes(key));
}

function normalizeActions(actionKeys: string[], roleName?: string | null) {
  const allowedKeys = new Set(
    ACTION_OPTIONS.filter((option) => roleName === 'ADMIN' || !option.adminOnly).map((option) => option.key)
  );
  return ACTION_OPTIONS.map((option) => option.key).filter((key) => allowedKeys.has(key) && actionKeys.includes(key));
}

function groupedMenuOptions(group: PermissionGroup) {
  return MENU_OPTIONS.filter((option) => group.items.includes(option.key) && (selectedRoleIsAdmin.value || !option.adminOnly));
}

function groupedActionOptions(group: PermissionGroup) {
  return ACTION_OPTIONS.filter((option) => group.items.includes(option.key) && (selectedRoleIsAdmin.value || !option.adminOnly));
}

async function submit() {
  if (!canManageUsers.value) {
    ElMessage.warning('当前角色没有用户管理权限');
    return;
  }
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
  if (!canManageRoles.value) {
    ElMessage.warning('当前角色没有角色配置权限');
    return;
  }
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
  if (selectedRoleIsAdmin.value && (!roleForm.actions.includes('user.manage') || !roleForm.actions.includes('role.manage'))) {
    ElMessage.warning('管理员角色必须保留用户管理和角色配置权限');
    return;
  }
  roleLoading.value = true;
  try {
    await updateRole(selectedRole.value.roleId, {
      roleDesc: roleForm.roleDesc,
      menus: normalizeMenus(roleForm.menus, selectedRole.value.roleName),
      actions: normalizeActions(roleForm.actions, selectedRole.value.roleName)
    });
    ElMessage.success('角色权限已更新，现有会话会自动同步；如仍停留旧状态，可手动刷新页面确认');
    await loadData();
  } catch (error) {
    ElMessage.error(`角色权限保存失败: ${(error as Error).message}`);
  } finally {
    roleLoading.value = false;
  }
}

async function handleDelete(user: UserSummary) {
  if (!canManageUsers.value) {
    ElMessage.warning('当前角色没有用户管理权限');
    return;
  }
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
        <el-alert
          v-if="!canManageUsers"
          class="notice-box"
          title="当前角色只有用户查看权限，不能新增、编辑或删除用户。"
          type="info"
          :closable="false"
        />
        <el-form label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" :disabled="!canManageUsers" />
          </el-form-item>
          <el-form-item :label="editingUserId ? '重置密码（留空则不修改）' : '登录密码'">
            <el-input v-model="form.password" show-password placeholder="请输入密码" :disabled="!canManageUsers" />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.roleId" placeholder="请选择角色" :disabled="!canManageUsers">
              <el-option
                v-for="role in roles"
                :key="role.roleId"
                :label="`${role.roleName} / ${role.roleDesc || ''}`"
                :value="role.roleId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status" :disabled="!canManageUsers">
              <el-radio-button label="ENABLED">ENABLED</el-radio-button>
              <el-radio-button label="DISABLED">DISABLED</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-button type="primary" :loading="loading" :disabled="!canManageUsers" @click="submit">{{ editingUserId ? '保存修改' : '创建用户' }}</el-button>
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
              <el-button v-if="canManageUsers" link type="primary" @click="loadUser(row)">编辑</el-button>
              <el-button v-if="canManageUsers" link type="danger" @click="handleDelete(row)">删除</el-button>
              <span v-if="!canManageUsers" class="inline-tip">仅查看</span>
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
            <span class="inline-tip">菜单和操作权限都从数据库读取，现有会话会定时同步。</span>
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
          <el-table-column label="操作权限">
            <template #default="{ row }">
              <div class="tag-cluster">
                <el-tag v-for="actionKey in row.actionPermissions" :key="`${row.roleId}-${actionKey}`" size="small" effect="plain" type="success">
                  {{ actionLabel(actionKey) }}
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
            :title="`当前角色：${selectedRole.roleName}。管理员角色必须保留“用户与权限”菜单、用户管理和角色配置权限，普通角色不会展示这些管理入口。`"
          />
          <el-alert
            v-if="!canManageRoles"
            type="warning"
            :closable="false"
            title="当前会话只有角色查看权限，不能修改角色配置。"
          />
          <el-form label-position="top">
            <el-form-item label="角色标识">
              <el-input :model-value="selectedRole.roleName" disabled />
            </el-form-item>
            <el-form-item label="角色说明">
              <el-input v-model="roleForm.roleDesc" placeholder="请输入角色说明" :disabled="!canManageRoles" />
            </el-form-item>
            <el-form-item label="菜单权限">
              <div class="permission-tree">
                <section
                  v-for="group in MENU_GROUPS"
                  :key="group.key"
                  v-show="groupedMenuOptions(group).length > 0"
                  class="permission-section"
                >
                  <div class="permission-section-header">
                    <strong>{{ group.label }}</strong>
                    <span class="inline-tip">{{ group.description }}</span>
                  </div>
                  <el-checkbox-group v-model="roleForm.menus" class="permission-grid">
                    <el-checkbox
                      v-for="option in groupedMenuOptions(group)"
                      :key="option.key"
                      :label="option.key"
                      :disabled="Boolean(!canManageRoles || (option.adminOnly && !selectedRoleIsAdmin))"
                      class="permission-item"
                    >
                      {{ option.label }}
                      <span v-if="option.adminOnly" class="permission-hint">仅管理员</span>
                    </el-checkbox>
                  </el-checkbox-group>
                </section>
              </div>
            </el-form-item>
            <el-form-item label="操作权限">
              <div class="permission-tree">
                <section
                  v-for="group in ACTION_GROUPS"
                  :key="group.key"
                  v-show="groupedActionOptions(group).length > 0"
                  class="permission-section"
                >
                  <div class="permission-section-header">
                    <strong>{{ group.label }}</strong>
                    <span class="inline-tip">{{ group.description }}</span>
                  </div>
                  <el-checkbox-group v-model="roleForm.actions" class="permission-grid">
                    <el-checkbox
                      v-for="option in groupedActionOptions(group)"
                      :key="option.key"
                      :label="option.key"
                      :disabled="Boolean(!canManageRoles || (option.adminOnly && !selectedRoleIsAdmin))"
                      class="permission-item"
                    >
                      {{ option.label }}
                      <span v-if="option.adminOnly" class="permission-hint">仅管理员</span>
                    </el-checkbox>
                  </el-checkbox-group>
                </section>
              </div>
            </el-form-item>
          </el-form>
          <div class="inline-tip">当前已选择 {{ roleForm.menus.length }} 个菜单：{{ selectedRoleMenuLabels.join('、') || '未选择' }}</div>
          <div class="inline-tip">当前已选择 {{ roleForm.actions.length }} 个操作：{{ selectedRoleActionLabels.join('、') || '未选择' }}</div>
          <div class="hero-actions">
            <el-button type="primary" :loading="roleLoading" :disabled="!canManageRoles" @click="saveRole">保存角色权限</el-button>
            <el-button @click="resetRoleForm">取消修改</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无可配置角色" />
      </el-card>
    </div>
  </div>
</template>
