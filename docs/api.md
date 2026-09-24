# 接口文档

## 通用约定

- 默认端口 `8080`，管理台首页为 `/admission/index.html`。
- 所有接口返回统一结构：

```json
{ "code": "000", "data": {}, "message": null }
```

| code | 含义 |
| --- | --- |
| `000` | 成功 |
| `001` | 参数校验或业务流程校验失败 |
| `010` | 未登录 |
| `100` | 系统异常（HTTP 状态码 500） |

- 除 `/login/**` 与 `/admission/**` 静态资源外，其余接口都需要先登录；未登录返回 `010` 与 HTTP 401。
- 登录状态保存在 HttpSession 中，浏览器会自动携带 `JSESSIONID`。

## 登录

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/login/doLogin` | `name`、`pass` | 登录，成功后重建会话 |
| GET | `/login/checkLogin` | 无 | 查询当前会话是否已登录 |
| GET | `/login/logout` | 无 | 退出登录，清除会话标记 |

```bash
curl -c cookies.txt -b cookies.txt "http://127.0.0.1:8080/login/doLogin?name=admin&pass=admin123"
```

## 流程状态

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/status/getStatus` | 返回当前流程状态序号（0-4） |
| GET | `/status/getLogList` | 返回全部流程日志 |
| GET | `/status/reset` | 重置系统：清空考生、专业、学院并回到初始状态 |

> `/status/reset` 会删除业务数据，仅在本地调试或重新导入时使用。

## 文件导入导出

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/file/uploadMajor` | `multipart/form-data`，字段名 `file` | 导入招生计划，会先清空原有计划与考生 |
| POST | `/file/uploadStudent` | `multipart/form-data`，字段名 `file` | 导入考生志愿，需先导入招生计划 |
| GET | `/file/exportResult` | 无 | 导出录取结果 xlsx，需在调剂完成后调用 |
| GET | `/file/exportExit` | 无 | 导出退档队列 xlsx，需在调剂完成后调用 |

```bash
curl -b cookies.txt -F "file=@excel/广东某大学招生计划.xlsx" "http://127.0.0.1:8080/file/uploadMajor"
curl -b cookies.txt -F "file=@excel/平行志愿考生测试数据.xlsx" "http://127.0.0.1:8080/file/uploadStudent"
curl -b cookies.txt -o result.xlsx "http://127.0.0.1:8080/file/exportResult"
```

招生计划 Excel 表头：`专业代号`、`专业代码`、`学院`、`专业名称`、`备注`、`学制年限`、`招生计划数`。

考生志愿 Excel 表头：`准考证号`、`姓名`、`总分`、`志愿1` ~ `志愿6`、`调剂`、`排位`、`省份`、`城市`、`科类`。

## 录取与调剂

| 方法 | 路径 | 前置状态 | 说明 |
| --- | --- | --- | --- |
| GET | `/student/doEnroll` | 已导入志愿 | 执行平行志愿投档 |
| GET | `/student/doAdjust` | 已录取 | 执行专业调剂 |

## 结果查询

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/student/getStudentRaw` | `currentPage` | 分页查看全部考生原始数据，每页 50 条 |
| GET | `/student/getResult` | `currentPage`、`desc`、`rank`、`departmentId`、`majorId` | 分页查询录取结果，可按排位 / 学院 / 专业过滤 |
| GET | `/student/searchStudentByCandidate` | `currentPage`、`keyword` | 按准考证号精确查询 |
| GET | `/student/getStudentBeforeRank` | `currentPage`、`rank` | 查询排位不高于指定值的考生 |
| GET | `/student/getAdjustStudentRaw` | `currentPage` | 查询待调剂考生 |
| GET | `/student/getExitStudentRaw` | `currentPage` | 查询退档考生 |

## 统计报表

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/student/getStatisticsResult` | 无 | 全体录取考生的最高 / 最低排位与分数、平均分 |
| GET | `/student/getStatisticsResultInDepartment` | 无 | 按学院统计 |
| GET | `/student/getStatisticsResultInMajor` | 无 | 按专业统计，含未录满专业 |
| GET | `/student/getDistribute` | 无 | 按省份统计录取人数 |
| GET | `/student/getDistributeInProvince` | `province` | 指定省份下按城市统计 |
| GET | `/student/getGradeDistribute` | 无 | 全体分数段分布 |
| GET | `/student/getGradeDistributeByDepartment` | `departmentId` | 指定学院分数段分布 |
| GET | `/student/getGradeDistributeByMajor` | `majorId` | 指定专业分数段分布 |
| GET | `/student/getCountDistributeInDepartment` | 无 | 各学院录取人数 |
| GET | `/student/getCountDistributeInMajor` | 无 | 各专业录取人数 |
| GET | `/student/getCountDistributeInMajorByDepartment` | `departmentId` | 指定学院下各专业录取人数 |

示例返回：

```json
{
  "code": "000",
  "data": [
    { "topRank": 1, "bottomRank": 6862, "maxGrade": 590, "minGrade": 517,
      "averageGrade": 538, "groupName": null }
  ],
  "message": null
}
```

## 招生计划与学院

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/major/getMajorPlan` | 无 | 查询招生计划与各专业实际录取人数 |
| GET | `/major/updateMajorPlan` | `majorId`、`count` | 修改专业招生计划数（需在投档前） |
| GET | `/major/getMajors` | 无 | 查询全部专业 |
| GET | `/major/getMajorsByDepartment` | `departmentId` | 查询指定学院的专业 |
| GET | `/department/getDepartments` | 无 | 查询全部学院 |

## 错误返回示例

```json
{ "code": "001", "data": null, "message": "当前状态不能执行录取，请先导入招生计划与考生志愿文件" }
```

```json
{ "code": "010", "data": null, "message": "未登录" }
```
