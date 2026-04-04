<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isAuthExpiredError } from '../api/client';
import {
  createDataSource,
  deleteDataSource,
  listDataSources,
  testDataSource,
  updateDataSource,
  updateDataSourceStatus,
  type DataSource
} from '../api/platform';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const dataSources = ref<DataSource[]>([]);
const editingSourceId = ref<number | null>(null);
const form = reactive({
  sourceName: '',
  sourceType: 'FILE',
  host: '',
  port: undefined as number | undefined,
  dbName: '',
  username: '',
  password: '',
  description: '',
  duplicateConnectionStrategy: 'WARN' as 'ALLOW' | 'WARN' | 'REJECT'
});
const canManageSources = computed(() => authStore.hasAction('source.manage'));

function resetForm() {
  editingSourceId.value = null;
  Object.assign(form, {
    sourceName: '',
    sourceType: 'FILE',
    host: '',
    port: undefined,
    dbName: '',
    username: '',
    password: '',
    description: '',
    duplicateConnectionStrategy: 'WARN'
  });
}

async function loadData() {
  dataSources.value = await listDataSources();
}

async function submit() {
  if (!canManageSources.value) {
    ElMessage.warning('当前角色没有数据源管理权限');
    return;
  }
  if (!form.sourceName || !form.sourceType) {
    ElMessage.warning('请填写数据源名称和类型');
    return;
  }
  try {
    const isEditing = Boolean(editingSourceId.value);
    const result = editingSourceId.value
      ? await updateDataSource(editingSourceId.value, form)
      : await createDataSource(form);
    const successMessage = isEditing ? '数据源更新成功' : '数据源创建成功';
    resetForm();
    await loadData();
    ElMessage.success(successMessage);
    if (result.warningMessage) {
      ElMessageBox.alert(result.warningMessage, '重复连接提示', {
        type: 'warning',
        confirmButtonText: '知道了'
      });
    }
  } catch (error) {
    ElMessage.error(`保存失败: ${(error as Error).message}`);
  }
}

function handleEdit(source: DataSource) {
  if (!canManageSources.value) {
    ElMessage.warning('当前角色没有数据源管理权限');
    return;
  }
  editingSourceId.value = source.sourceId;
  Object.assign(form, {
    sourceName: source.sourceName || '',
    sourceType: source.sourceType || 'FILE',
    host: source.host || '',
    port: source.port || undefined,
    dbName: source.dbName || '',
    username: source.username || '',
    password: '',
    description: source.description || '',
    duplicateConnectionStrategy: 'WARN'
  });
}

async function handleTest(sourceId: number) {
  if (!canManageSources.value) {
    ElMessage.warning('当前角色没有数据源管理权限');
    return;
  }
  try {
    const result = await testDataSource(sourceId);
    ElMessage.success(result.message);
  } catch (error) {
    ElMessage.error(`测试失败: ${(error as Error).message}`);
  }
}

async function handleToggleStatus(source: DataSource) {
  if (!canManageSources.value) {
    ElMessage.warning('当前角色没有数据源管理权限');
    return;
  }
  const nextStatus = source.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  const actionText = nextStatus === 'ENABLED' ? '启用' : '停用';
  try {
    await ElMessageBox.confirm(`确定${actionText}数据源「${source.sourceName}」吗？`, '状态确认', { type: 'warning' });
    await updateDataSourceStatus(source.sourceId, nextStatus);
    ElMessage.success(`数据源已${actionText}`);
    await loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`状态更新失败: ${(error as Error).message}`);
    }
  }
}

async function handleDelete(sourceId: number) {
  if (!canManageSources.value) {
    ElMessage.warning('当前角色没有数据源管理权限');
    return;
  }
  try {
    await deleteDataSource(sourceId);
    ElMessage.success('数据源已删除');
    await loadData();
  } catch (error) {
    ElMessage.error(`删除失败: ${(error as Error).message}`);
  }
}

onMounted(async () => {
  try {
    await loadData();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
    ElMessage.error(`数据源加载失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid two-column-grid">
    <el-card v-if="canManageSources" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ editingSourceId ? '编辑数据源' : '新增数据源' }}</span>
          <el-tag type="success">Real API</el-tag>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="数据源名称">
          <el-input v-model="form.sourceName" />
        </el-form-item>
        <el-form-item label="数据源类型">
          <el-select v-model="form.sourceType" class="form-select-medium">
            <el-option label="FILE" value="FILE" />
            <el-option label="MYSQL" value="MYSQL" />
          </el-select>
        </el-form-item>
        <el-form-item label="同连接处理策略">
          <el-select v-model="form.duplicateConnectionStrategy" class="form-select-wide">
            <el-option label="WARN（允许保存并提示，推荐）" value="WARN" />
            <el-option label="REJECT（发现重复则拒绝）" value="REJECT" />
            <el-option label="ALLOW（允许保存且不提示）" value="ALLOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址">
          <el-input v-model="form.host" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" class="full-width" />
        </el-form-item>
        <el-form-item label="数据库名">
          <el-input v-model="form.dbName" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" show-password :placeholder="editingSourceId ? '留空则保留原密码' : ''" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-alert
          title="同名数据源始终禁止保存；若 MYSQL 连接配置与现有数据源重复，系统将按当前策略执行允许、提示或拒绝。"
          type="info"
          :closable="false"
        />
        <div class="card-header-actions">
          <el-button type="primary" @click="submit">{{ editingSourceId ? '保存修改' : '创建数据源' }}</el-button>
          <el-button v-if="editingSourceId" @click="resetForm">取消编辑</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card v-else shadow="never">
      <el-empty description="当前角色只有数据源查看权限，不能新增、测试或删除数据源。" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据源管理</span>
          <el-button link type="primary" @click="loadData">刷新</el-button>
        </div>
      </template>
      <el-table :data="dataSources" stripe>
        <el-table-column prop="sourceName" label="数据源名称" />
        <el-table-column prop="sourceType" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="description" label="说明" />
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button v-if="canManageSources" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="canManageSources"
              link
              :type="row.status === 'ENABLED' ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="canManageSources" link type="success" @click="handleTest(row.sourceId)">测试连接</el-button>
            <el-button v-if="canManageSources" link type="danger" @click="handleDelete(row.sourceId)">删除</el-button>
            <span v-if="!canManageSources" class="inline-tip">仅查看</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
