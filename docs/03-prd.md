# 产品需求文档（PRD）

> 文档编号：PRD-001 ｜ 版本 v1.0 ｜ 状态：已实现 ｜ 日期：2026-08-30

## 一、产品功能总览

系统提供 **4 个客户端视图 + 1 个管理端视图**（前端 Vue3 + Element Plus 实现）：

| 视图 | 功能 | 关联用户故事 |
| --- | --- | --- |
| 产品与申购 | 在售产品列表（代码/名称/风险/费率/起购/状态）、申购弹窗、赎回弹窗 | US1.1、US1.2、US2.1 |
| 订单查询 | 申购/赎回订单 Tab 切换、分页筛选、详情抽屉（含状态轨迹时间线） | US3.1、US3.2 |
| 持仓与资金 | 资金账户三卡片（余额/冻结/可用）、持仓表（总/冻结/可用份额） | US4.1、US4.2 |
| 管理端 | 发布净值、触发 T+1 确认批处理、结果统计 | US5.2 |
| （管理端扩展） | 产品维护、交易日历、账户开立（API 已具备，前端后续迭代） | US5.1、US5.3、US5.4 |

## 二、页面与交互说明

### 2.1 产品与申购

- 顶部展示「今日要处理」提示区（逾期/今日待办置顶，标注红色逾期 + 一键处理）
- 产品表格列：产品代码、产品名称、风险等级、申购费率、起购金额、状态、操作（申购/赎回）
- 申购弹窗：客户编号（默认 C001）、申购金额（数字输入，≥起购金额）
- 赎回弹窗：客户编号、赎回份额（不能超过可用份额）
- 提交后展示订单号与 T 日；失败时展示后端业务提示（如"申购金额不能低于起购金额 1000.00"）

### 2.2 订单查询

- 申购/赎回订单 Tab 切换；分页（默认 10 条）
- 订单列表列：订单号、产品、金额/份额、T 日、状态（待冻结/已冻结/已确认/确认失败）
- 详情抽屉：全字段 + 状态流转轨迹时间线（OrderPlaced → FundsFrozen → SubscriptionConfirmed 等）

### 2.3 持仓与资金

- 资金账户：账户余额、冻结金额、可用金额（¥ 展示，支出红/收入绿习惯）
- 持仓表：产品代码、产品名称、总份额、冻结份额、可用份额

### 2.4 管理端

- ① 发布净值：选择产品 + 净值日期 + 净值（4 位小数）
- ② 触发 T+1 确认：选择 T 日 → 执行 → 展示申购/赎回确认成功失败笔数

## 三、REST API 契约

> 统一前缀 `/api`；统一响应体 `{ code, message, data }`；`code=0` 成功；业务错误返回 HTTP 200 + 业务码（前端按 code 提示）；日期 `yyyy-MM-dd`；金额元（2 位小数）；分页 `pageNum`（从 1 起）/`pageSize`。

### 3.1 客户端接口

| 方法 | 路径 | 说明 | 关键请求参数 | 成功响应 data | 主要错误码 |
| --- | --- | --- | --- | --- | --- |
| GET | /api/products | 在售基金产品列表（分页） | productName（模糊）、pageNum、pageSize | 产品列表（代码/名称/净值/费率/风险等级/状态） | — |
| GET | /api/products/{productId} | 产品详情 | 路径参数 | 产品完整信息 + 最新净值 + 赎回费率梯度 | 40401 产品不存在 |
| POST | /api/orders/subscription | 提交申购 | customerId、productId、subscriptionAmount | orderId、tDay、status、冻结金额 | 40001 产品暂停申购；40002 低于起购金额；40003 资金不足 |
| POST | /api/orders/redemption | 提交赎回 | customerId、productId、redemptionShares | orderId、tDay、status、冻结份额 | 40004 可用份额不足；40005 产品暂停赎回 |
| GET | /api/orders/subscription | 分页查询申购单 | customerId（必填）、status、productId、dateFrom、dateTo、pageNum、pageSize | 申购单分页列表（订单号/产品/金额/状态/T日/确认份额/确认净值） | — |
| GET | /api/orders/redemption | 分页查询赎回单 | 同上（份额维度） | 赎回单分页列表 | — |
| GET | /api/orders/{orderId} | 订单详情（申购/赎回通用） | 路径参数 | 订单全字段 + 状态流转轨迹列表（时间/前状态/后状态/触发事件） | 40402 订单不存在 |
| GET | /api/customers/{customerId}/positions | 持仓列表 | customerId | 每产品：总份额/可用份额/冻结份额/最新净值/参考市值 | — |
| GET | /api/customers/{customerId}/positions/{productId} | 单产品持仓明细 | 路径参数 | 份额明细 + 该产品近期交易记录 | 40403 无持仓 |
| GET | /api/customers/{customerId}/funds-account | 资金账户信息 | customerId | 总余额/冻结金额/可用余额 | — |

### 3.2 管理端接口

| 方法 | 路径 | 说明 | 关键请求参数 | 说明 |
| --- | --- | --- | --- | --- |
| POST | /api/admin/products | 创建基金产品 | 产品代码/名称/费率/起购金额/风险等级 | 创建后默认「在售」 |
| PUT | /api/admin/products/{productId}/status | 产品状态变更 | targetStatus（ON_SALE / SUSPENDED） | 发布 ProductSuspended 事件 |
| POST | /api/admin/net-values | 录入并发布净值 | productId、navDate、value | 发布 NetValuePublished 事件 |
| POST | /api/admin/trade-calendar | 维护交易日历 | date、isTradeDay（或节假日区间批量） | 供交易日历领域服务查询 |
| POST | /api/admin/customers/{customerId}/accounts | 开立 TA 账户 + 资金账户 | 初始入金金额（可选） | 返回 TA 账号 |
| POST | /api/admin/confirmations/run | 手动触发 T+1 确认批处理 | tDay（可选，默认按日历推断） | 返回确认成功/失败笔数（联调与测试用） |

### 3.3 错误码规范

| 码 | 含义 |
| --- | --- |
| 0 | 成功 |
| 40000 | 参数非法（领域层校验） |
| 40001 | 产品暂停申购 |
| 40002 | 低于起购金额 |
| 40003 | 资金不足 |
| 40004 | 可用份额不足 |
| 40005 | 产品暂停赎回 |
| 40401 | 产品不存在 |
| 40402 | 订单不存在 |
| 40403 | 无持仓 |
| 50000 | 系统内部错误 |

## 四、验收标准

> 验收以 [BRD 第五章 GIVEN/WHEN/THEN 业务场景](02-brd.md) 为准，全部 20 个场景通过即验收通过。

**附加验收点：**
1. 前端移动端可用（单列布局、按钮 ≥44px、输入框 ≥16px）
2. 订单详情展示完整状态轨迹
3. 业务错误前端显示具体提示（非"Request failed"）
4. 数据持久化：刷新/重开不丢失；多设备数据一致（云数据库）
5. 免费部署公网可访问

## 五、版本记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-08-30 | 首版实现并上线（申购/赎回/订单/持仓/管理端全流程） |
