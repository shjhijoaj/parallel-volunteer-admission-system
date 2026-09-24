# 来源与致谢

## 项目来源

本项目的业务原型来自公开的课程设计项目 **admissionsystem_backend**（平行志愿录取系统），
其 README 中标注为广东工业大学数据库课程设计，原始仓库地址：

- 后端：<https://github.com/Baibair/admissionsystem_backend>
- 前端：<https://github.com/Baibair/admissionsystem_frontend>

前端静态资源（`src/main/resources/static/admission/`）与 `images/` 目录下的界面截图同样来自上述项目。

## 本项目在其基础上的改造

| 方向 | 具体改动 |
| --- | --- |
| 架构 | 把投档与调剂规则从 Service 中抽出为独立的 `EnrollEngine`，与数据库解耦，可单独测试 |
| 安全 | 移除仓库中的真实数据库密码与硬编码管理员口令，全部改为环境变量注入 |
| 安全 | 登录拦截器由「恒返回 true」改为基于 HttpSession 的真实校验，登录成功后重建会话 |
| 安全 | 修复 `updatePlanStudentCount` 使用 `${}` 拼接导致的 SQL 注入风险 |
| 数据安全 | 取消启动时自动执行含 `DROP TABLE` 的建表脚本，改为 `SQL_INIT_MODE` 显式控制 |
| 缺陷修复 | 修复 EasyExcel 监听器中 `static` 集合导致的并发导入数据串号 |
| 缺陷修复 | 修复同一秒内多条流程日志导致的录取状态读取不确定 |
| 缺陷修复 | 修复调剂池使用内连接、导致无人填报的缺额专业无法参与调剂的问题 |
| 缺陷修复 | 移除全局 `Map` 保存 Session 的实现，改为标准 HttpSession 属性 |
| 依赖升级 | Spring Boot 2.3.4 → 2.7.18，EasyExcel 2.2.6 → 3.3.4，Druid 1.1.10 → 1.2.20 |
| 测试 | 新增 8 项规则单元测试与 2 项 MySQL 端到端集成测试 |
| 工程化 | 新增 Dockerfile、docker-compose、GitHub Actions、启动与验证脚本 |
| 文档 | 新增架构、数据模型、接口、测试文档与运行证据 |

## 第三方组件

| 组件 | 许可证 |
| --- | --- |
| Spring Boot | Apache License 2.0 |
| MyBatis / MyBatis-Spring-Boot-Starter | Apache License 2.0 |
| Druid | Apache License 2.0 |
| EasyExcel | Apache License 2.0 |
| PageHelper | MIT |
| Vue 2 / Element UI / ECharts | MIT / MIT / Apache License 2.0 |

感谢上述开源项目与原作者提供的基础实现。
