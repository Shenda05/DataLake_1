<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { createTask, listFlows, listImportHistory, listTasks, pauseTask, resumeTask, triggerTask, type GovernanceFlow, type TaskRecord } from '../api/platform';

const tasks = ref<TaskRecord[]>([]);
const flows = ref<GovernanceFlow[]>([]);
const imports = ref<any[]>([]);
const form = reactive({
  taskName: '',
  taskType: 'GOVERNANCE',
  targetId: undefined as number | undefined,
  cronExpr: '0 0/5 * * * *',
  description: '',
  status: 'ENABLED'
});

async function loadBaseData() {
  tasks.value = await listTasks();
  flows.value = await listFlows();
  imports.value = await listImportHistory();
}

async function submit() {
  if (!form.taskName || !form.targetId) {
    ElMessage.warning('请填写任务名称并选择执行对象');
    return;
  }
  try {
    await createTask({
      taskName: form.taskName,
      taskType: form.taskType,
      targetId: form.targetId,
      cronExpr: form.cronExpr,
      description: form.description,
      status: form.status
    });
    ElMessage.success('任务创建成功');
    Object.assign(form, { taskName: '', taskType: 'GOVERNANCE', targetId: undefined, cronExpr: '0 0/5 * * * *', description: '', status: 'ENABLED' });
    await loadBaseData();
  } catch (error) {
    ElMessage.error(`创建任务失败: ${(error as Error).message}`);
  }
}

async function handleTrigger(taskId: number) {
  try {
    await triggerTask(taskId);
    ElMessage.success('任务已触发');
    await loadBaseData();
  } catch (error) {
    ElMessage.error(`触发失败: ${(error as Error).message}`);
  }
}

async function handlePause(taskId: number) {
  try {
    await pauseTask(taskId);
    ElMessage.success('任务已暂停');
    await loadBaseData();
  } catch (error) {
    ElMessage.error(`暂停失败: ${(error as Error).message}`);
  }
}

async function handleResume(taskId: number) {
  try {
    await resumeTask(taskId);
    ElMessage.success('任务已恢复');
    await loadBaseData();
  } catch (error) {
    ElMessage.error(`恢复失败: ${(error as Error).message}`);
  }
}

onMounted(async () => {
  try {
    await loadBaseData();
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
          <span>新建任务</span>
          <el-tag>IMPORT / GOVERNANCE</el-tag>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="任务名称">
          <el-input v-model="form.taskName" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType">
            <el-option label="GOVERNANCE" value="GOVERNANCE" />
            <el-option label="IMPORT" value="IMPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行对象">
          <el-select v-model="form.targetId">
            <el-option
              v-for="item in form.taskType === 'GOVERNANCE' ? flows : imports"
              :key="form.taskType === 'GOVERNANCE' ? item.flowId : item.importId"
              :label="form.taskType === 'GOVERNANCE' ? item.flowName : item.datasetName"
              :value="form.taskType === 'GOVERNANCE' ? item.flowId : item.importId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron 表达式">
          <el-input v-model="form.cronExpr" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-button type="primary" @click="submit">保存任务</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <el-button link type="primary" @click="loadBaseData">刷新</el-button>
        </div>
      </template>
      <el-table :data="tasks" stripe>
        <el-table-column prop="taskName" label="任务名称" />
        <el-table-column prop="taskType" label="类型" width="140" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="nextRunTime" label="下次执行时间" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleTrigger(row.taskId)">立即执行</el-button>
            <el-button link type="warning" @click="handlePause(row.taskId)">暂停</el-button>
            <el-button link type="success" @click="handleResume(row.taskId)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
