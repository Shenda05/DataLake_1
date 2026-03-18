# 数据湖管理平台

这是一个面向课程项目的数据湖管理平台仓库骨架，按 `frontend + backend + docs + database` 四层组织，目的是把现有的产品设计文档直接落成一个可以分工开发的工程起点。

## 当前已落地内容

- `frontend/`：Vue 3 + Element Plus + Pinia + ECharts 的后台管理前端骨架。
- `backend/`：Spring Boot 风格的后端工程骨架，包含认证、仪表盘、数据源、数据集、治理、任务和日志接口入口。
- `database/`：核心 9 个实体的 MySQL 建表脚本。
- `docs/`：接口约定、架构说明、周计划与测试计划。

## 建议开发顺序

1. 先完成 `docs/api-contract.md` 和 `database/schema.sql` 的评审。
2. 前端按 `frontend/src/router/index.ts` 的菜单路由分模块开发。
3. 后端按 `backend/src/main/java/com/datalake/platform` 下的模块目录分接口开发。
4. 第一次联调优先打通 `登录 -> 首页 -> 数据源 -> 数据导入 -> 数据集列表`。

## 本地启动

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认使用本地 Mock 数据，适合 Week 1-2 并行开发。

### 后端

后端要求 `JDK 17+`。当前机器默认 `javac` 是 11，如需使用本机已安装的 JDK 18，可执行：

```bash
cd backend
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run
```

后端当前以接口骨架和示例数据为主，重点是先固定接口结构、统一返回值和模块边界。

如果本机 `8080` 已被占用，可临时改端口启动：

```bash
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
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

- Week 3 开始将前端 Mock 接口逐步替换为真实后端接口。
- Week 4 前冻结字段命名、分页格式、异常码与任务日志结构。
- Week 6 前保证治理结果以“新数据集”方式落库，避免覆盖原始数据。
