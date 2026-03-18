<script setup lang="ts">
import { dataSources } from '../mock/api';
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据源管理</span>
          <el-button type="primary">新增数据源</el-button>
        </div>
      </template>
      <el-table :data="dataSources" stripe>
        <el-table-column prop="sourceName" label="数据源名称" />
        <el-table-column prop="sourceType" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="description" label="说明" />
        <el-table-column label="操作" width="220">
          <template #default>
            <el-button link type="primary">编辑</el-button>
            <el-button link type="success">测试连接</el-button>
            <el-button link type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

