# 课程平台数据模型

| 表 | 主要字段与职责 |
| --- | --- |
| cs_user | 唯一用户名、展示名、PBKDF2 摘要、ADMIN/STUDENT 角色 |
| cs_round | 批次标题、说明、状态、截止时间、创建时间 |
| cs_course | 所属批次、名称、老师、地点、时间、名额、描述 |
| cs_application | 批次、学生、JSON 意向顺序、调剂选择、优先分、课程结果、结果类型 |
| cs_audit | 批次、操作者、动作、摘要、操作时间 |

一个批次包含多门课程和多条报名；一位学生在同批次最多一条报名，由 UNIQUE(round_id,user_id) 约束。课程结果外键指向 cs_course。应用层验证志愿课程属于同一批次、不能重复且最多六个。字段的实际 DDL 见 CourseService.run，启动仅 CREATE IF NOT EXISTS，不执行 DROP。

result_type：1–6 对应命中的意向顺序，7 为调剂，-1 为未分配，-2 为待分配。外部页面显示业务描述，不要求用户理解编码。日期按应用 Asia/Shanghai 本地时间处理，部署时保持时区一致。
