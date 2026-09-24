# 测试与验证

## 一键验证

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/verify.ps1
```

脚本依次执行：

1. 编译与单元测试（不需要数据库）。
2. 检查 `ENROLL_IT` 与数据库连接配置，满足条件时执行 MySQL 端到端集成测试。
3. 输出结果摘要与证据文件位置。

## 单元测试

文件：`src/test/java/org/enroll/service/EnrollEngineTest.java`

这 8 个用例只依赖 `EnrollEngine`，不启动 Spring、不连接数据库，可以在任何环境下执行：

| 用例 | 验证内容 |
| --- | --- |
| `shouldAdmitToFirstWillWhenPlanAvailable` | 第一志愿有余额时直接投档，并把已录人数加一 |
| `shouldSkipFullWillAndAdmitToNext` | 前两个志愿都录满时投到第三志愿，`acceptedType = 3` |
| `shouldIgnoreBlankAndUnknownWills` | 空白志愿与不在计划中的专业代号被跳过 |
| `shouldWaitForAdjustWhenAllWillsFullAndAdjustAccepted` | 志愿全部落空且服从调剂时返回待调剂状态 `0` |
| `shouldExitWhenAllWillsFullAndRefuseAdjust` | 志愿全部落空且不服从调剂时返回退档状态 `-1` |
| `shouldNotExceedPlanCount` | 计划 2 人时第三名考生必须退档，实际录取人数停留在 2 |
| `adjustShouldFillVacanciesInOrderAndExitRest` | 缺额池按顺序补录，缺额用完后剩余考生退档 |
| `adjustShouldSkipFullMajors` | 调剂时跳过已录满的专业 |

## 集成测试

文件：`src/test/java/org/enroll/AdmissionFlowIntegrationTest.java`

需要真实 MySQL，通过环境变量开启：

```powershell
$env:ENROLL_IT      = "true"
$env:MYSQL_HOST     = "127.0.0.1"
$env:MYSQL_PORT     = "3306"
$env:MYSQL_DATABASE = "enroll_it"
$env:MYSQL_USER     = "root"
$env:MYSQL_PASSWORD = "<password>"
mvn test
```

> 集成测试会把 `spring.sql.init.mode` 设为 `always`，启动时执行包含 `DROP TABLE` 的建表脚本。
> **必须指向独立的测试库**，不要指向生产库或有数据的开发库。

覆盖场景：

- `shouldRunWholeAdmissionFlow`：插入 1 个学院、3 个专业、5 名考生，
  验证第 1 / 第 2 志愿命中、A 满后顺延到 B、服从调剂落入 C、不服从调剂退档，
  并校验「专业实际录取人数不超过计划数」与导出文件非空。
- `resetShouldClearAdmissionState`：验证重置后状态回到初始值、考生与专业数据被清空。

## 手工验收

```powershell
# 1. 启动
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/run-dev.ps1

# 2. 登录（浏览器访问）
http://127.0.0.1:8080/admission/index.html

# 3. 按顺序操作
#    上传招生计划 -> 上传考生志愿 -> 执行录取 -> 执行调剂 -> 导出结果
```

验收检查点：

| 检查项 | 期望结果 |
| --- | --- |
| 未登录访问 `/status/getStatus` | HTTP 401，`code = 010` |
| 登录后再访问 | `code = 000` |
| 上传招生计划后查询状态 | `status = 1` |
| 上传考生志愿后查询状态 | `status = 2` |
| 未导入数据直接执行录取 | `code = 001`，提示当前状态不能录取 |
| 执行录取后查询状态 | `status = 3` |
| 执行调剂后查询状态 | `status = 4` |
| 调剂完成后导出的两份 Excel | 文件可正常打开，包含录取 / 退档记录 |
| 检查 `t_major` | 不存在 `realistic_student_count > plan_student_count` 的行 |

## 运行证据

`docs/evidence/` 保存了一次完整运行的结果：

| 文件 | 说明 |
| --- | --- |
| `verification-summary.json` | 测试项、实测指标与一致性校验结果 |
| `application-run.log` | 应用启动与运行日志 |
| `export-result.xlsx` | 实际导出的录取结果（6409 + 257 条） |
| `export-exit.xlsx` | 实际导出的退档队列（196 条） |

## 已知边界

- 未做并发投档压测；当前实现把投档放在单个事务中，适合教学与单实例场景。
- 未覆盖前端页面的自动化回归，前端为已构建产物。
