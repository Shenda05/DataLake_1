# 数据湖管理平台

这是一个面向课程项目的数据湖管理平台工程仓库，按 `frontend + backend + docs + database` 四层组织。当前 `develop` 已经把“登录 -> 首页 -> 数据源 -> 文件导入 -> 数据集列表”这条主链路从 Mock 替换成了真实后端接口。

## 当前已落地内容

- `frontend/`：Vue 3 + Element Plus + Pinia + ECharts 前端，登录、首页、数据源、文件导入和数据集页已接入真实 API。
- `backend/`：Spring Boot 后端，已实现 JWT 登录鉴权、仪表盘聚合、数据源持久化、文件导入、元数据生成和数据集列表/预览。
- `database/`：包含 `sys_user`、`sys_role`、`data_source`、`import_record`、`data_set`、`meta_field` 等表结构。
- `docs/`：接口约定、架构说明、周计划与测试计划。
- `storage/`：运行时自动生成，用于保存上传原文件。

## 当前主链路
1. 使用默认账号登录，前端从后端获取 token 和菜单权限。
2. 首页从数据库读取统计卡片、近 7 天任务趋势和最近任务。
3. 管理员可以创建、测试和删除数据源。
4. 文件导入支持 `CSV / JSON / Excel`，导入后自动生成数据集和元数据。
5. 数据集页支持真实列表、元数据查看和分页预览。

## 本地启动

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:8080/api`。如果后端改了端口，可以这样启动：

```bash
VITE_API_BASE_URL=http://localhost:8081/api npm run dev
```

### 后端

后端要求 `JDK 17+`。当前机器默认 `javac` 是 11，如需使用本机已安装的 JDK 18，可执行：

```bash
cd backend
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run
```

后端默认使用 H2 内存库，启动时会自动初始化表结构和默认账号。

如果本机 `8080` 已被占用，可临时改端口启动：

```bash
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

如果需要切到 MySQL，可先创建数据库 `data_lake_platform`，再使用：

```bash
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## 目录结构

```text
.
├── backend
├── database
├── docs
├── frontend
├── 产品功能设计文档.md
├── 小组成员分工.md
└── 数据湖管理平台_选题与功能.md
```

## 默认账号

- 管理员：`admin / admin123`
- 普通用户：`operator / operator123`

## 下一步建议

- 继续把查询分析页切到真实接口。
- 为数据预览补搜索和字段筛选。
- 在 MySQL 环境补一轮完整联调，确认文件存储路径和字符集设置。
