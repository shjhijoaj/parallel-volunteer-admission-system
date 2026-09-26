# 课程平台 API

JSON 接口前缀 /api。先 GET /api/session 获取 csrf，并保留 Cookie；写操作携带 X-CSRF-Token。登录会轮换会话 ID，退出会销毁会话，之后重新获取 Token。错误响应为 message，常见状态 400/401/403/409/429。

| 方法与路径 | 用途 | 角色 |
| --- | --- | --- |
| GET /health | 数据库可达检查 | 公开 |
| POST /auth/register | username/display_name/password | 公开 |
| POST /auth/login、/auth/logout | 登录/退出 | 当前用户 |
| GET /auth/me | 当前账户 | 登录 |
| POST /auth/password | 修改密码并注销 | 登录 |
| GET /rounds、/rounds/{id} | 批次列表/详情 | 按角色过滤 |
| POST /rounds、PUT /rounds/{id} | 创建/编辑草稿 | 管理员 |
| POST /rounds/{id}/courses | 添加课程 | 管理员 |
| PUT/DELETE /rounds/{rid}/courses/{cid} | 修改/删除课程 | 管理员 |
| POST /rounds/{id}/apply | preferences 数组、allow_adjust | 学生 |
| DELETE /rounds/{id}/apply | 撤回报名 | 学生 |
| POST /rounds/{rid}/applications/{aid}/score | priority_score 0–1000 | 管理员 |
| POST /rounds/{id}/open、close、allocate、publish | 状态推进 | 管理员 |
| GET /rounds/{id}/template.xlsx | 课程模板 | 管理员 |
| POST /rounds/{id}/courses.xlsx | multipart file 上传 | 管理员 |
| GET /rounds/{id}/results.xlsx | 结果导出 | 管理员 |

数据字段以 CourseController、ExcelController 和 CourseWorkflowTest 为准。不要调用旧 /student、/major、/file 录取接口；当前应用不启用它们。
