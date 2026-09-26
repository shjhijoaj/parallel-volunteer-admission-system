# 课选测试记录

本地 Java 17 + H2，2026-09-26：Maven package 成功，19 项被发现，18 项通过，1 项旧 MySQL 环境测试跳过。

- EnrollEngineTest：8 项，覆盖志愿匹配、容量、调剂等纯算法场景。
- CourseWorkflowTest：10 项，覆盖报名与分配、无效/跨批次意向、截止校验、导入原子性、并发分配、账号权限、公示隐私、CSRF、改密失效、撤回与顺序、健康检查/模板。
- 真实浏览器：管理员登录、创建批次及三门课、开放报名；学生注册、选择志愿、提交；老师关闭、分配、公示、Excel 导出；学生查结果。1440px 与 390px 布局通过，无 pageerror。

## 重跑
`./mvnw test`（Windows 为 mvnw.cmd）。不需要 MySQL。当前课程集成测试使用独立 H2 库，不读用户 data。

浏览器测试使用独立服务实例：设置 PORT=8089、COURSE_DB_URL 指向 test-results 中的新库、BOOTSTRAP_ADMIN_PASSWORD 为测试口令，启动应用。安装 `npm install --no-save playwright@1.55.0` 与 `npx playwright install chromium`，设置 COURSE_ADMIN_PASSWORD 同一测试口令，运行 `node tools/browser-acceptance.cjs`。脚本会创建模拟账户及批次，不能对正式数据运行。Windows 可设置 BROWSER_CHANNEL=msedge。

未声称完成：新版 MySQL 迁移验收、多人压力测试、真实学校部署、灾难恢复演练。旧 docs/evidence 与旧 MySQL 用例属于原录取版本，不能当成本版课程平台的验证结果。
