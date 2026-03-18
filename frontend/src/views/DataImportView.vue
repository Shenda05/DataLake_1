<template>
  <div class="page-grid two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>文件上传导入</span>
          <el-tag type="warning">Week 3</el-tag>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="所属数据源">
          <el-select placeholder="请选择数据源">
            <el-option label="本地上传目录" value="1001" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集名称">
          <el-input placeholder="请输入数据集名称" />
        </el-form-item>
        <el-form-item label="文件格式">
          <el-select placeholder="请选择格式">
            <el-option label="CSV" value="CSV" />
            <el-option label="JSON" value="JSON" />
            <el-option label="Excel" value="EXCEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload drag action="#">
            <div>拖拽文件到此处，或点击上传</div>
          </el-upload>
        </el-form-item>
        <el-button type="primary">提交导入</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>导入规则</span>
          <el-tag>课程项目简化</el-tag>
        </div>
      </template>
      <ul class="plain-list">
        <li>支持 CSV、JSON、Excel 三类文件。</li>
        <li>导入失败需要返回格式错误、字段缺失或大小超限原因。</li>
        <li>导入成功后自动生成数据集和元数据信息。</li>
        <li>Week 4 开始接入预览和元数据列表。</li>
      </ul>
    </el-card>
  </div>
</template>

