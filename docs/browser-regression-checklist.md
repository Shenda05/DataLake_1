# 浏览器回归检查单

这份检查单用于补齐当前还无法完全通过接口脚本覆盖的浏览器层验证，重点覆盖：

- 权限页实时同步
- 首页快捷入口与菜单收口
- 日志页按钮级授权
- 数据库表导入的页面交互
- `xlsx` 文件的人工打开验证
- 日志回放后的页面反馈

## 建议准备

1. 启动前端开发服务或预览服务
2. 使用 MySQL profile 启动后端
3. 先执行 [mysql-regression-check.sh](/Users/xyd/Desktop/DataLake/scripts/mysql-regression-check.sh) 准备真实数据和基础校验

最近一次脚本预检已在 `2026-03-27` 通过，样本结果为：

- `importedTable=operator_def`
- `importedDatasetId=11`
- `logCountBeforeReplay=8`
- `logCountAfterReplay=9`

示例：

```bash
cd backend
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' \
PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH \
MYSQL_USERNAME='root' \
MYSQL_PASSWORD='你的密码' \
mvn spring-boot:run -Dspring-boot.run.profiles=mysql -Dspring-boot.run.arguments=--server.port=8093

BASE_URL=http://127.0.0.1:8093/api \
MYSQL_SOURCE_USER='root' \
MYSQL_SOURCE_PASSWORD='你的密码' \
bash scripts/mysql-regression-check.sh
```

## 1. 权限页实时同步

### 操作步骤

1. 浏览器 A 使用 `admin / admin123` 登录
2. 浏览器 B 使用 `operator / operator123` 登录
3. 在浏览器 A 的“用户与权限”页选中 `OPERATOR`
4. 去掉 `log.export`，保存角色权限
5. 保持浏览器 B 停留在“日志监控”页，等待自动同步或手动刷新
6. 再把 `log.export` 勾回去并保存

### 期望结果

- 浏览器 B 的日志导出按钮会在权限移除后变为禁用或消失
- 浏览器 B 不需要重新登录
- 恢复权限后，日志导出按钮重新可用

## 2. 首页快捷入口与菜单收口

### 操作步骤

1. 用管理员账号进入首页
2. 观察 Hero 区、统计卡片、最近任务、最近日志和演示捷径中的跳转按钮
3. 用普通用户账号进入首页
4. 在管理员页临时移除 `OPERATOR` 的某个菜单，例如 `governance` 或 `tasks`
5. 返回普通用户首页并刷新

### 期望结果

- 管理员能看到完整的快捷入口
- 普通用户只会看到当前角色拥有菜单权限的入口
- 被移除的菜单入口会从首页快捷区和相关卡片中自动消失
- 点击保留下来的入口可以正常跳转到对应页面

## 3. 日志页按钮级授权

### 操作步骤

1. 使用普通用户进入“日志监控”
2. 观察顶部导出按钮、日志列表中的“回放”、详情页中的“一键回放”
3. 在管理员页临时移除 `log.replay`
4. 返回普通用户日志页并刷新
5. 再恢复 `log.replay`

### 期望结果

- 有 `log.export` 时，可以导出日志
- 有 `log.replay` 时，任务日志上会出现回放按钮
- 移除 `log.replay` 后，回放按钮消失或不可点
- 详情页跳回“任务页 / 来源页”时，会受对应菜单权限影响

## 4. 数据库表导入页面

### 操作步骤

1. 用管理员进入“数据接入”
2. 切换到“数据库表导入”
3. 选择脚本创建的 MySQL 数据源
4. 点击“刷新表列表”
5. 选择 `operator_def`、`sys_user` 或脚本实际导入的那张表
6. 点击“预览数据”
7. 执行“导入为数据集”

### 期望结果

- 表列表能正常加载
- 预览区能看到字段和样例数据
- 导入成功后会生成新的数据集和导入历史
- 数据集页中可以看到对应数据集

## 5. Excel 导出人工打开验证

### 操作步骤

1. 进入“数据集管理”，选择一个刚导入或治理生成的数据集
2. 点击“导出 Excel”
3. 进入“查询分析”，执行一次条件查询或 SQL 查询
4. 点击“导出 Excel”
5. 分别用 Excel 或 WPS 打开两个下载文件

### 期望结果

- 文件可以正常打开
- 工作表存在，列头完整
- 中文内容不乱码
- 数字列和文本列没有明显错位

## 6. 日志回放页面反馈

### 操作步骤

1. 在“日志监控”选择一条由任务生成的日志
2. 点击“回放”或“一键回放”
3. 观察日志列表、任务页和首页事件流

### 期望结果

- 页面会给出成功反馈
- 日志列表条数增加
- 最近日志和首页事件流会出现新的执行记录
- 如果回放的是治理任务，可能会生成新的输出数据集

## 建议记录方式

- 每一项都记录“是否通过 / 失败截图 / 备注”
- 如果失败，优先记录：
  - 当前账号
  - 当前角色菜单权限
  - 当前角色操作权限
  - 当前页面 URL
  - 浏览器控制台首条红色报错
