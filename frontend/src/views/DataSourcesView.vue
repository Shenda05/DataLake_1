<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { createDataSource, deleteDataSource, listDataSources, testDataSource, type DataSource } from '../api/platform';

const dataSources = ref<DataSource[]>([]);
const form = reactive({
  sourceName: '',
  sourceType: 'FILE',
  host: '',
  port: undefined as number | undefined,
  dbName: '',
  username: '',
  password: '',
  description: ''
});

async function loadData() {
  dataSources.value = await listDataSources();
}

async function submit() {
  if (!form.sourceName || !form.sourceType) {
    ElMessage.warning('请填写数据源名称和类型');
    return;
  }
  try {
    await createDataSource(form);
    ElMessage.success('数据源创建成功');
    Object.assign(form, { sourceName: '', sourceType: 'FILE', host: '', port: undefined, dbName: '', username: '', password: '', description: '' });
    await loadData();
  } catch (error) {
    ElMessage.error(`创建失败: ${(error as Error).message}`);
  }
}

async function handleTest(sourceId: number) {
  try {
    const result = await testDataSource(sourceId);
    ElMessage.success(result.message);
  } catch (error) {
    ElMessage.error(`测试失败: ${(error as Error).message}`);
  }
}

async function handleDelete(sourceId: number) {
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
    ElMessage.error(`数据源加载失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>新增数据源</span>
          <el-tag type="success">Real API</el-tag>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="数据源名称">
          <el-input v-model="form.sourceName" />
        </el-form-item>
        <el-form-item label="数据源类型">
          <el-select v-model="form.sourceType">
            <el-option label="FILE" value="FILE" />
            <el-option label="MYSQL" value="MYSQL" />
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
          <el-input v-model="form.password" show-password />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-button type="primary" @click="submit">创建数据源</el-button>
      </el-form>
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
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="success" @click="handleTest(row.sourceId)">测试连接</el-button>
            <el-button link type="danger" @click="handleDelete(row.sourceId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
