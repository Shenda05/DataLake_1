<script setup lang="ts">
import { datasets, metadata } from '../mock/api';
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据集列表</span>
          <el-tag type="success">Week 4</el-tag>
        </div>
      </template>
      <el-table :data="datasets" stripe>
        <el-table-column prop="datasetName" label="数据集名称" />
        <el-table-column prop="formatType" label="格式" width="120" />
        <el-table-column prop="recordCount" label="记录数" width="120" />
        <el-table-column prop="fieldCount" label="字段数" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="creator" label="创建人" width="120" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>元数据预览</span>
          <el-tag>MetaField</el-tag>
        </div>
      </template>
      <el-table :data="metadata" stripe>
        <el-table-column prop="fieldName" label="字段名" />
        <el-table-column prop="fieldType" label="字段类型" width="120" />
        <el-table-column prop="nullable" label="可为空" width="120" />
        <el-table-column prop="fieldDesc" label="字段说明" />
        <el-table-column prop="sampleValue" label="样例值" />
      </el-table>
    </el-card>
  </div>
</template>

