# 系统架构

## 分层结构

```mermaid
flowchart TD
    subgraph 前端
        V[Vue 2 管理台<br/>表格 / 图表 / 文件上传]
    end
    subgraph Web 层
        LI[LoginInterceptor]
        GEH[GlobalExceptionHandler]
        C1[LoginController]
        C2[StatusController]
        C3[StudentController]
        C4[MajorController]
        C5[DepartmentController]
        C6[FileController]
    end
    subgraph Service 层
        S1[StudentServiceImpl<br/>投档 / 调剂 / 查询编排]
        S2[ExcelServiceImpl<br/>导入 / 导出]
        S3[MajorServiceImpl / StatusServiceImpl]
        EE[EnrollEngine<br/>纯规则引擎]
    end
    subgraph 持久层
        M1[StudentMapper]
        M2[MajorMapper]
        M3[DepartmentMapper]
        M4[StatusMapper]
        DB[(MySQL 8)]
    end
    V --> LI --> C1 & C2 & C3 & C4 & C5 & C6
    C3 --> S1
    C6 --> S2
    C4 --> S3
    C2 --> S3
    S1 --> EE
    S1 --> M1 & M2 & M4
    S2 --> M1 & M2 & M3 & M4
    S3 --> M2 & M3 & M4
    M1 & M2 & M3 & M4 --> DB
```

各层职责：

| 层 | 职责 | 说明 |
| --- | --- | --- |
| Controller | 参数接收、结果包装 | 只做参数校验与调用，不写业务规则 |
| Service | 业务流程编排 | 事务边界、分页、状态校验、调用规则引擎 |
| EnrollEngine | 投档与调剂规则 | 纯函数，不依赖 Spring 与数据库，可直接单测 |
| Mapper | SQL 执行 | XML 中的动态 SQL 与分页插件配合 |

## 录取状态机

状态保存在 `t_log` 的最新一条记录中，每一步都必须满足前置状态。

```mermaid
stateDiagram-v2
    [*] --> START: 系统初始 / 重置
    START --> WITHOUT_STUDENT: 导入招生计划
    WITHOUT_STUDENT --> START: 重新导入招生计划（清空考生）
    WITHOUT_STUDENT --> FILE_READY: 导入考生志愿
    FILE_READY --> ENROLLED: 执行平行志愿投档
    ENROLLED --> ADJUSTED: 执行专业调剂
    ADJUSTED --> [*]: 导出结果
```

| 状态 | 序号 | 含义 |
| --- | --- | --- |
| `START` | 0 | 尚未导入任何数据，或已执行重置 |
| `WITHOUT_STUDENT` | 1 | 已导入招生计划，尚未导入考生志愿 |
| `FILE_READY` | 2 | 计划与志愿齐备，可以执行投档 |
| `ENROLLED` | 3 | 投档完成，可以执行调剂或导出 |
| `ADJUSTED` | 4 | 调剂完成，可以导出最终结果 |

## 考生录取状态

`t_student.accepted_type` 是结果字段，取值范围与含义：

| 取值 | 含义 |
| --- | --- |
| `-2` | 初始值，尚未参与投档 |
| `-1` | 退档（志愿全部落空且不服从调剂，或调剂缺额已用完） |
| `0` | 服从调剂，等待调剂 |
| `1` ~ `6` | 被第 N 个志愿录取 |
| `7` | 被专业调剂录取 |

## 投档流程

```mermaid
sequenceDiagram
    participant S as StudentServiceImpl
    participant M as StudentMapper
    participant E as EnrollEngine
    participant DB as MySQL
    S->>DB: 读取当前状态，必须为 FILE_READY
    S->>DB: 查询招生计划（专业代号 + 计划数）
    loop 每批 200 名考生
        S->>M: getStudentRawForEnroll(start, 200) 按排位升序
        M-->>S: 考生列表
        S->>E: enroll(考生, 计划表)
        E-->>S: 命中的志愿号或调剂 / 退档
        S->>M: updateAccepted(批量回写)
    end
    S->>DB: 回写各专业实际录取人数
    S->>DB: 记录日志，状态推进到 ENROLLED
```

关键设计：

- **分页处理**：每批 200 条，避免一次性加载全部考生；`current` 按批推进，不回退。
- **内存计数**：投档过程中专业余额在内存中累加，最后一次性回写 `realistic_student_count`。
- **规则外置**：`EnrollEngine.enroll` 只负责「第一个有余额的志愿」，Service 不关心具体规则细节。

## 调剂流程

1. 查询所有 `plan_student_count > realistic_student_count` 的专业作为缺额池。
   查询使用 `LEFT JOIN`，保证**从未有人投档的专业**也能进入缺额池。
2. 按排位升序读取 `adjust = 1 AND accepted_type = 0` 的考生。
3. 依次把考生分配进缺额池，缺额池用完后剩余考生置为退档。
4. 回写专业实际录取人数与考生状态，状态推进到 `ADJUSTED`。

> 调剂分页读取时**不能推进偏移量**：被调剂的考生 `accepted_type` 由 0 变为 7，
> 会立刻从结果集中消失，推进偏移量会漏掉后续排位的考生。

## 数据流与文件

```mermaid
flowchart LR
    F1[招生计划 Excel] --> R1[ReadMajorListener<br/>20 条一批] --> T1[(t_department / t_major)]
    F2[考生志愿 Excel] --> R2[ReadStudentListener<br/>20 条一批] --> T2[(t_student)]
    T1 & T2 --> EN[投档] --> AD[调剂]
    AD --> Q[统计查询]
    AD --> X1[录取结果 Excel]
    AD --> X2[退档队列 Excel]
```

## 已知限制

- 状态依赖 `t_log` 最新记录，属于轻量实现；多实例部署时需要改为数据库状态表加乐观锁。
- 前端为已构建产物，仓库内不含前端源码工程。
- 投档与调剂在单事务中完成，数据量进一步增大时可改为分片事务 + 断点续跑。
