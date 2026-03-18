<script setup lang="ts">
const users = [
  { username: 'admin', role: 'ADMIN', status: 'ENABLED' },
  { username: 'operator', role: 'OPERATOR', status: 'ENABLED' }
];
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户与权限</span>
          <el-button type="primary">新增用户</el-button>
        </div>
      </template>
      <el-table :data="users" stripe>
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="role" label="角色" width="140" />
        <el-table-column prop="status" label="状态" width="140" />
        <el-table-column label="操作" width="180">
          <template #default>
            <el-button link type="primary">编辑</el-button>
            <el-button link type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

