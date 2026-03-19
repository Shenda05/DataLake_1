<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listRoles, listUsers } from '../api/platform';

const users = ref<any[]>([]);
const roles = ref<any[]>([]);

async function loadData() {
  users.value = await listUsers();
  roles.value = await listRoles();
}

onMounted(async () => {
  try {
    await loadData();
  } catch (error) {
    ElMessage.error(`用户与角色加载失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <el-tag>管理员视图</el-tag>
        </div>
      </template>
      <el-table :data="users" stripe>
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="role" label="角色" width="140" />
        <el-table-column prop="status" label="状态" width="140" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>角色列表</span>
          <el-button link type="primary" @click="loadData">刷新</el-button>
        </div>
      </template>
      <el-table :data="roles" stripe>
        <el-table-column prop="roleId" label="角色ID" width="100" />
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column prop="roleDesc" label="角色说明" />
      </el-table>
    </el-card>
  </div>
</template>
