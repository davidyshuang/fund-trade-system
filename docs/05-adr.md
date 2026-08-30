# 架构决策记录（ADR）

> 记录本项目关键架构决策与理由，含踩坑复盘，供后续演进参考。

## ADR-001：采用 DDD 四层架构 + 领域事件驱动

- **状态**：已采纳
- **背景**：系统涉及交易、资金、TA 账户、估值、产品、客户 6 个限界上下文，跨上下文协作频繁
- **决策**：后端按标准 DDD 四层（api / application / domain / infrastructure）组织；跨上下文一律通过**领域事件**（进程内事件总线，Spring ApplicationEvent 适配）解耦，禁止上下文之间直接调用领域模型
- **理由**：业务规则（状态机、费率、不变量）收敛于领域层，可独立单测；上下文解耦便于演进为真实微服务
- **后果**：application 层承担流程编排与事务边界；引入 12 个领域事件

## ADR-002：技术栈选型（JDK21 + Spring Boot 3.x + Vue3 + Element Plus）

- **状态**：已采纳
- **决策**：前端 Vue3 + Vite + Element Plus；后端 JDK21 + Spring Boot 3.2（spring-boot-starter-web/jdbc）；JUnit5 + MockMvc
- **理由**：JDK21 支持 record/模式匹配简化值对象；Spring Boot 3 稳定生态；Vue3 + Element Plus 快速搭建管理型界面
- **后果**：领域层保持零框架依赖（纯 Java），便于单测与复用

## ADR-003：数据库 SQLite → PostgreSQL

- **状态**：已采纳（M2 阶段从 SQLite 迁移）
- **背景**：初版按"单文件 + 零运维"选 SQLite；后需云部署（多设备共享数据），SQLite 不支持远程访问
- **决策**：生产/云使用 PostgreSQL 16（Neon 免费版）；测试库用 PostgreSQL 独立库
- **理由**：PostgreSQL 是云数据库事实标准；Neon 提供免费 Serverless PG；DDD 仓储接口不变，仅换基础设施实现
- **迁移要点**：`INSERT OR REPLACE`→`ON CONFLICT ... DO UPDATE`；`INSERT OR IGNORE`→`ON CONFLICT DO NOTHING`；`datetime('now')`→`LOCALTIMESTAMP`；`AUTOINCREMENT`→`BIGSERIAL`；金额等仍以 TEXT 存储（BigDecimal 字符串）

## ADR-004：金额/份额精度方案（BigDecimal + TEXT 存储）

- **状态**：已采纳
- **决策**：金额/份额/净值/费率统一用 `BigDecimal` 值对象（金额/份额 2 位、净值 4 位、HALF_UP）；数据库以 **TEXT 列**存 BigDecimal 字符串
- **理由**：杜绝浮点误差；TEXT 存储避免数据库 NUMERIC 精度/方言差异
- **后果**：读取时 `Money.of(String)` 反序列化；SQL 层无精度转换

## ADR-005：业务错误返回 HTTP 200 + 业务码（而非 HTTP 400）

- **状态**：已采纳
- **背景**：初版业务异常返回 HTTP 400，导致前端 axios 只报 "Request failed with status code 400"，吞掉业务提示
- **决策**：业务规则拦截（产品暂停/低于起购/资金不足等）统一返回 **HTTP 200 + `{code, message, data}`**；`code=0` 成功，非 0 为业务错误码；仅框架级错误（JSON 解析失败、500）保留 HTTP 语义
- **理由**：HTTP 状态码表达传输/系统层语义，业务结果由响应体承载；前端统一按 code 提示
- **后果**：前端 axios 响应拦截器 `body.code === 0` 判断成功；错误拦截器优先展示 `err.response.data.message`

## ADR-006：T+1 确认批处理（双通道触发）

- **状态**：已采纳
- **决策**：T+1 确认由管理端手动触发（`POST /api/admin/confirmations/run`）+ 定时任务双通道；确认逻辑幂等（按状态机拦截重复确认）
- **理由**：演示环境手动触发便于联调；定时任务为生产兜底
- **后果**：确认批处理消费 NetValuePublished 事件 + 按 tDay 扫描待确认订单

## ADR-007：云部署方案（Render + Cloudflare Pages + Neon）

- **状态**：已采纳
- **决策**：后端 Docker 部署到 Render（免费实例，Java 用 Docker 因为 Render 无原生 Java runtime）；前端 Vite 构建产物部署到 Cloudflare Pages（Git 集成自动构建）；数据库用 Neon 免费 PostgreSQL
- **理由**：三平台均有免费额度；GitHub 集成 push 自动部署；符合演示系统成本诉求
- **部署要点**：后端 `Dockerfile` 多阶段构建（maven 构建 → JRE21 运行）；`render.yaml` Blueprint；前端 Cloudflare 配置 Root directory=`frontend`、Build=`npm run build`、Output=`dist`、`VITE_API_BASE` 指向 Render 地址；wrangler v4 用 `[assets]` 语法

## ADR-008：CORS 开放（演示环境）

- **状态**：已采纳
- **决策**：`/api/**` 允许跨域（`allowedOriginPatterns("*")`）
- **理由**：前端 Cloudflare 与后端 Render 域名不同，必须跨域；演示环境放开便于访问
- **风险**：生产应收紧为具体前端域名；部署后链接公网可访问，勿录真实数据

## ADR-009：演示客户账户硬编码 C001

- **状态**：已采纳（演示简化）
- **决策**：前端当前固定使用演示客户 C001（初始资金 20000）
- **理由**：简化演示流程；多客户切换留待迭代（C002 账户已预置 50000）
- **后果**：演示账户资金被测试消耗后需重置（Neon SQL 或清库重建）
