<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  createTask,
  listGovernanceFlows,
  listImportHistory,
  listTasks,
  pauseTask,
  resumeTask,
  triggerTask,
  updateTask,
  type GovernanceFlow,
  type ImportHistory,
  type TaskSummary
} from '../api/platform';

const tasks = ref<TaskSummary[]>([]);
const flows = ref<GovernanceFlow[]>([]);
const importHistory = ref<ImportHistory[]>([]);
const editingTaskId = ref<number | null>(null);
const loading = ref(false);
const form = reactive({
  taskName: '',
  taskType: 'GOVERNANCE' as 'IMPORT' | 'GOVERNANCE',
  targetId: undefined as number | undefined,
  cronExpr: '0 */5 * * * *',
  retryPolicy: 1,
  description: '',
  status: 'ENABLED'
});

const targetOptions = computed(() => {
  if (form.taskType === 'IMPORT') {
    return importHistory.value.map((item) => ({
      value: item.importId,
      label: `${item.datasetName} / ${item.formatType} / ${item.createTime}`
    }));
  }
  return flows.value.map((item) => ({
    value: item.flowId,
    label: `${item.flowName} / ${item.inputDatasetName}`
  }));
});

async function loadData() {
  tasks.value = await listTasks();
  flows.value = await listGovernanceFlows();
  importHistory.value = await listImportHistory();
  if (!form.targetId && targetOptions.value.length > 0) {
    form.targetId = targetOptions.value[0].value;
  }
}

function resetForm() {
  editingTaskId.value = null;
  form.taskName = '';
  form.taskType = 'GOVERNANCE';
  form.targetId = targetOptions.value[0]?.value;
  form.cronExpr = '0 */5 * * * *';
  form.retryPolicy = 1;
  form.description = '';
  form.status = 'ENABLED';
}

function loadTask(task: TaskSummary) {
  editingTaskId.value = task.taskId;
  form.taskName = task.taskName;
  form.taskType = task.taskType;
  form.targetId = task.targetId;
  form.cronExpr = task.cronExpr;
  form.retryPolicy = task.retryPolicy;
  form.description = task.description || '';
  form.status = task.status === 'PAUSED' ? 'PAUSED' : 'ENABLED';
}

async function submit() {
  if (!form.taskName || !form.targetId || !form.cronExpr) {
    ElMessage.warning('请填写任务名称、目标和 Cron 表达式');
    return;
  }
  loading.value = true;
  try {
    const payload = {
      taskName: form.taskName,
      taskType: form.taskType,
      targetId: form.targetId,
      cronExpr: form.cronExpr,
      retryPolicy: form.retryPolicy,
      description: form.description,
      status: form.status
    };
    if (editingTaskId.value) {
      await updateTask(editingTaskId.value, payload);
      ElMessage.success('任务更新成功');
    } else {
      await createTask(payload);
      ElMessage.success('任务创建成功');
    }
    resetForm();
    await loadData();
  } catch (error) {
    ElMessage.error(`任务保存失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function handleTrigger(taskId: number) {
  try {
    const result = await triggerTask(taskId);
    ElMessage.success(result.message);
    await loadData();
  } catch (error) {
    ElMessage.error(`触发失败: ${(error as Error).message}`);
  }
}

async function handlePause(taskId: number) {
  try {
    await pauseTask(taskId);
    ElMessage.success('任务已暂停');
    await loadData();
  } catch (error) {
    ElMessage.error(`暂停失败: ${(error as Error).message}`);
  }
}

async function handleResume(taskId: number) {
  try {
    await resumeTask(taskId);
    ElMessage.success('任务已恢复');
    await loadData();
  } catch (error) {
    ElMessage.error(`恢复失败: ${(error as Error).message}`);
  }
}

onMounted(async () => {
  try {
    await loadData();
    if (targetOptions.value.length > 0) {
      form.targetId = targetOptions.value[0].value;
    }
  } catch (error) {
    ElMessage.error(`任务页初始化失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ editingTaskId ? '编辑任务' : '新建任务' }}</span>
          <div>
            <el-button link type="primary" @click="resetForm">重置</el-button>
            <el-button type="primary" :loading="loading" @click="submit">
              {{ editingTaskId ? '保存修改' : '创建任务' }}
            </el-button>
          </div>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="任务名称">
          <el-input v-model="form.taskName" placeholder="例如 每日治理流程执行" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" @change="form.targetId = targetOptions[0]?.value">
            <el-option label="GOVERNANCE" value="GOVERNANCE" />
            <el-option label="IMPORT" value="IMPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行目标">
          <el-select v-model="form.targetId" placeholder="请选择目标">
            <el-option
              v-for="item in targetOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron 表达式">
          <el-input v-model="form.cronExpr" placeholder="例如 0 */5 * * * *" />
        </el-form-item>
        <el-form-item label="失败重试次数">
          <el-input-number v-model="form.retryPolicy" :min="1" :max="5" class="full-width" />
        </el-form-item>
        <el-form-item label="初始状态">
          <el-select v-model="form.status">
            <el-option label="ENABLED" value="ENABLED" />
            <el-option label="PAUSED" value="PAUSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务说明">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <el-alert
        class="notice-box"
        title="当前版本支持 IMPORT 和 GOVERNANCE 两类真实任务，调度器会按 nextRunTime 轮询执行"
        type="info"
        :closable="false"
      />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务调度列表</span>
          <el-button link type="primary" @click="loadData">刷新</el-button>
        </div>
      </template>
      <el-table :data="tasks" stripe @row-click="loadTask">
        <el-table-column prop="taskName" label="任务名称" />
        <el-table-column prop="taskType" label="类型" width="120" />
        <el-table-column prop="targetName" label="执行目标" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="nextRunTime" label="下次执行时间" width="180" />
        <el-table-column prop="lastRunTime" label="最近执行时间" width="180" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleTrigger(row.taskId)">立即执行</el-button>
            <el-button link type="warning" @click.stop="handlePause(row.taskId)">暂停</el-button>
            <el-button link type="success" @click.stop="handleResume(row.taskId)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
