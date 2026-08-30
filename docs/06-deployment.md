# 部署与 CI/CD 指南（从零开始）

> 适用人群：无任何部署经验、想把这套系统免费部署到公网、并理解其自动发布机制的人。
> 成本：**0 元**（三个平台均用免费额度）。耗时：首次部署约 30 分钟；之后 push 自动上线。

## 一、系统架构与地址总览

```
浏览器
  │
  ▼
Cloudflare Pages（前端 Vue3，免费静态托管 + 全球 CDN）
  │  /api（已配 CORS 跨域）
  ▼
Render（后端 Java 21 + SpringBoot，免费实例）
  │  jdbc:postgresql://...（sslmode=require）
  ▼
Neon（PostgreSQL 16 云数据库，免费 0.5GB）
```

**✅ 当前已上线可用地址：**

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| 前端（Cloudflare Pages） | https://fund-trade-system.931655086.workers.dev | 手机/电脑直接打开 |
| 后端（Render API） | https://fund-trade-system.onrender.com/api/products | 返回产品 JSON |
| 文档站（GitHub Pages） | https://davidyshuang.github.io/fund-trade-system/ | 本套文档 |
| 代码仓库 | https://github.com/davidyshuang/fund-trade-system | 公开仓库 |

## 二、前置准备（3 个免费账号）

1. **GitHub**：https://github.com —— 代码托管 + CI
2. **Neon**：https://neon.tech —— PostgreSQL 云数据库（GitHub 登录）
3. **Render**：https://render.com —— 后端托管（GitHub 登录）
4. **Cloudflare**：https://dash.cloudflare.com —— 前端托管（GitHub 登录）

## 三、第 0 步：准备代码

**方式 A：已有仓库（推荐）**
```bash
git clone git@github.com:davidyshuang/fund-trade-system.git
cd fund-trade-system
```

**方式 B：自己搭建**（本系统代码结构）
```
├── backend/     # Java 21 + SpringBoot 3.2 + PostgreSQL（DDD 四层，74 个测试）
├── frontend/    # Vue3 + Vite + Element Plus（四视图）
├── docs/        # 文档站（本目录）
├── Dockerfile   # 后端镜像（多阶段构建，供 Render 用）
├── render.yaml  # Render 部署蓝图
├── mkdocs.yml   # 文档站配置
└── .github/workflows/ci.yml   # GitHub Actions 自动测试
└── .github/workflows/docs.yml # 文档站自动发布
```

## 四、第 1 步：创建数据库（Neon，约 3 分钟）

1. 打开 https://neon.tech → GitHub 登录
2. 创建项目 → 区域建议选 **Singapore**（离国内更近）或 us-east-2
3. 进入 **Connection Details**，复制连接串，形如：
   ```
   postgresql://neondb_owner:真实密码@ep-xxx.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
4. 记下 6 个值（后面 Render 要用）：
   | 值 | 示例 |
   |---|---|
   | DB_HOST | `ep-xxx.us-east-2.aws.neon.tech`（注意：**去掉 `-pooler.c-5`**，用直连） |
   | DB_PORT | `5432` |
   | DB_NAME | `neondb` |
   | DB_USERNAME | `neondb_owner` |
   | DB_PASSWORD | 真实密码 |
   | DB_SSLMODE | `require`（Neon 强制 SSL） |

> ⚠️ 表结构无需手动建：系统启动时自动建 8 张表 + 写入演示数据（2 只基金、2 个账户、节假日）。

## 五、第 2 步：部署后端（Render，约 5 分钟）

### 方式 A：Blueprint 一键（推荐）
1. https://render.com → GitHub 注册
2. **New +** → **Blueprint** → 选仓库 `fund-trade-system`
   （自动读取根目录 `render.yaml`，Java 用 **Docker** 构建——Render 无原生 Java runtime）
3. Apply 后等首次构建（约 5~10 分钟，含拉 Maven 镜像）
4. **关键**：部署完成后进服务 → **Environment** 页，填入第 1 步的 6 个变量：
   ```
   DB_HOST=ep-xxx.us-east-2.aws.neon.tech
   DB_PORT=5432
   DB_NAME=neondb
   DB_USERNAME=neondb_owner
   DB_PASSWORD=真实密码
   DB_SSLMODE=require
   ```
5. **Save Changes**（自动重新部署）
6. 等服务变 **Live**，访问 `https://fund-trade-system.onrender.com/api/products` 应返回产品 JSON

### 方式 B：手动 Docker（替代方式）
Render → New Web Service → 选仓库 → Runtime 选 Docker → 同样填环境变量。

> 💡 后端健康检查路径已在配置里指定 `/api/products`。

## 六、第 3 步：部署前端（Cloudflare Pages，约 5 分钟）

### 方式 A：Dashboard Git 集成（推荐，push 自动更新）
1. https://dash.cloudflare.com → **Workers & Pages** → **Create** → **Pages** → **Connect to Git**
2. 授权并选择仓库 `fund-trade-system`
3. **构建设置（关键，4 个字段分开填）：**

   | 字段 | 值 |
   |---|---|
   | **Root directory** | `frontend`（不是 `/`！package.json 在子目录） |
   | **Build command** | `npm run build`（只填这个！） |
   | **Build output directory** | `dist` |
   | **Build variables** | `VITE_API_BASE` = `https://fund-trade-system.onrender.com` |

4. **Deploy command** 字段：新版 UI **必填**，填 `npx wrangler deploy`
   （仓库 `frontend/wrangler.toml` 已配好 wrangler v4 的 `[assets] directory = "./dist"` 语法）
5. **Save and Deploy** → 等待构建完成，成功日志：
   ```
   Success: Build command completed
   Deployed fund-trade-system triggers
   https://fund-trade-system.931655086.workers.dev   ← 你的前端地址
   ```

### 方式 B：本地 wrangler CLI（备选）
```bash
cd frontend
VITE_API_BASE=https://fund-trade-system.onrender.com npm run build
npx wrangler login        # 首次授权
npx wrangler deploy       # 读取 wrangler.toml，上传 dist
```

## 七、部署后验证清单

| 检查项 | 命令/操作 | 期望 |
|---|---|---|
| 后端健康 | 浏览器打开 `https://fund-trade-system.onrender.com/api/products` | 返回 `{"code":0,...,"data":{"list":[...]}}` |
| 申购流程 | `curl -X POST https://fund-trade-system.onrender.com/api/orders/subscription -H "Content-Type: application/json" -d '{"customerId":"C001","productId":"P001","subscriptionAmount":"10000.00"}'` | 返回订单号 + `FUNDS_FROZEN` |
| 前端页面 | 打开 workers.dev 地址 | 右上角绿色「后端已连接」+ 2 只基金产品列表 |
| 管理端 | 页面「管理端」Tab → 发布净值 → 触发 T+1 确认 | 返回确认成功计数 |

## 八、日常更新（改代码自动上线）

```bash
git add -A && git commit -m "xxx" && git push origin main
```
- **GitHub Actions**：自动跑后端 74 测试 + 前端构建（质量门禁）
- **Cloudflare**：自动重新构建部署前端
- **Render**：autoDeploy 自动重新构建部署后端
- **GitHub Pages**：docs/ 变更自动重新发布文档站

## 九、CI/CD 持续集成与持续部署

### 9.1 总览：一次 `git push` 触发什么

```
git push origin main
    │
    ├─▶ 🔵 GitHub Actions「CI」        → 质量门禁（74 测试 + 前端构建）
    ├─▶ 🟢 Render 自动部署（后端）     → autoDeploy 检测到 push
    ├─▶ 🟠 Cloudflare 自动部署（前端） → Git 集成检测到 push
    └─▶ 🔴 GitHub Actions「Docs」      → 仅 docs/ 变更时触发 → github.io 自动更新
```

四条链路全部**自动触发、零人工干预**。

### 9.2 CI（持续集成）— `.github/workflows/ci.yml`

**触发条件**：push 到 `main`/`master` 或任何 PR

```yaml
on:
  push:
    branches: [ main, master ]
  pull_request:
```

**两个并行 Job**（任意一个失败 = PR 被拦）：

| Job | 环境 | 做什么 |
| --- | --- | --- |
| `backend-test` | JDK21（temurin）+ **PostgreSQL 16 服务容器** | `mvn test` → 74 个测试（领域单测 + 集成 + API 冒烟）跑在真实 PG 上 |
| `frontend-build` | Node 22 | `npm ci` + `npm run build` → 前端可构建性验证 |

**PostgreSQL 服务容器**（测试基础设施）：

```yaml
services:
  postgres:                    # 起一个临时 PG 16 容器
    image: postgres:16
    ports: [5432:5432]
    env: { POSTGRES_USER: postgres, POSTGRES_PASSWORD: postgres, POSTGRES_DB: fund_trade_test }
# 测试时注入连接串（测试配置 PgTestConfig 读取环境变量）
TEST_DB_URL: jdbc:postgresql://localhost:5432/fund_trade_test
```

> 作用：每次 push 自动验证"代码没坏"，PR 合并前自动把关，无需人肉跑测试。

### 9.3 CD 链路 1：后端 → Render

**原理**：`render.yaml` 中 `autoDeploy: true` —— Render 监听 GitHub 仓库，检测到 main 新 commit 自动重新部署：

```
push → Render 收到通知 → 重新 Docker 构建（Dockerfile）→ 起新容器 → 切流
```

- **构建**：多阶段 Dockerfile（`mvn package` → JRE21 运行镜像）
- **环境变量**：Neon 连接（`DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD/DB_SSLMODE`）
- **健康检查**：`/api/products`，不健康自动标记失败

```yaml
# render.yaml 关键配置
- type: web
  name: fund-trade-backend
  runtime: docker        # Render 无原生 Java runtime，必须 Docker
  plan: free
  autoDeploy: true
  healthCheckPath: /api/products
```

### 9.4 CD 链路 2：前端 → Cloudflare Pages

**原理**：Cloudflare Pages 的 **Git 集成**（连接 GitHub 仓库），push 后自动构建：

```
push → Cloudflare 拉代码 → cd frontend → npm ci && npm run build → 上传 dist/ → 发布
```

**关键配置**（Cloudflare Dashboard 上填写，见第六章）：

| 字段 | 值 |
| --- | --- |
| Root directory | `frontend`（子目录！） |
| Build command | `npm run build` |
| Build output directory | `dist` |
| Build variables | `VITE_API_BASE = https://fund-trade-system.onrender.com` |
| Deploy command | `npx wrangler deploy`（配合 `frontend/wrangler.toml` 的 `[assets]` 语法） |

> 构建产物哈希变化即新版本（如 `index-CgoIaJM9.js`），可通过 assets 文件名判断是否已更新。

### 9.5 CD 链路 3：文档站 → GitHub Pages — `.github/workflows/docs.yml`

**触发**：仅当 `docs/**` 或 `mkdocs.yml` 变更（避免每次 push 都重建文档）

```yaml
on:
  push:
    branches: [ main ]
    paths:
      - 'docs/**'
      - 'mkdocs.yml'
      - '.github/workflows/docs.yml'
  workflow_dispatch:

run: mkdocs gh-deploy --force
```

**流程**：docs/ 变更 → Actions 构建静态站 → 强制推送 `gh-pages` 分支 → GitHub Pages 自动发布。

> ⚠️ 注意：GitHub Pages 的 `gh-pages` 分支部署**仅对公开仓库免费**；私有仓库需付费计划（本项目已转公开）。

### 9.6 触发矩阵（改什么 → 触发什么）

| 改了什么 | CI 测试 | Render 后端 | Cloudflare 前端 | 文档站 |
| --- | --- | --- | --- | --- |
| `backend/**` | ✅ | ✅ | — | — |
| `frontend/**` | ✅ | — | ✅ | — |
| `docs/**` 或 `mkdocs.yml` | ✅ | — | — | ✅ |
| `Dockerfile`/`render.yaml` | ✅ | ✅ | — | — |
| 其他（README 等） | ✅（保守全量） | — | — | — |

> CI 目前对所有 push 全量运行（未做 paths 过滤），是**保守策略**；如需提速可给 `ci.yml` 增加 `paths`。

### 9.7 自动触发机制揭秘：Webhook vs GitHub App

> 常见疑问：GitHub 仓库并没有配置 webhook，Render/Cloudflare 是怎么知道有新 commit 的？

**事实核查结果**：仓库 `Settings → Webhooks` 列表为 **空**（`gh api .../hooks` 返回 `[]`），没有传统 Repository Webhook；但部署确实每次 push 自动触发。

**真相：靠「GitHub App 集成」，不是传统 webhook**

Render 和 Cloudflare 连接 GitHub 时，走的是 **GitHub App（应用集成）** 机制：

```
你 git push
    │
    ▼
GitHub 平台内部事件（push）
    │
    ├─▶ GitHub Actions 直接触发            ← 平台内，无需 webhook
    │
    ├─▶ GitHub App「Render」账号级 webhook  ← App 服务器收到 → 过滤出本仓库 → 自动部署
    │
    └─▶ GitHub App「Cloudflare」账号级 webhook ← 同理 → 自动构建
```

**两种机制对比：**

| 机制 | 在哪里注册 | 仓库 Webhooks 列表能看到吗 |
| --- | --- | --- |
| 传统 Repository Webhook | 每个仓库 `Settings → Webhooks` | ✅ 能看到（URL 形如 `api.render.com/v1/webhooks/...`） |
| **GitHub App 集成**（Render/Cloudflare 所用） | **账号级**安装，App 服务器注册全局端点 | ❌ 看不到（GitHub 把事件推给 App 自身，不在仓库 hooks 里） |

**如何在网页验证**：
1. 仓库 → **Settings → Integrations**：`https://github.com/<owner>/<repo>/settings/installations`
2. 或账号头像 → **Settings → Applications → Installed GitHub Apps**
3. 应能看到 **Render** 与 **Cloudflare** 已安装且关联本仓库

**四条触发链路归属总结：**

| 触发链路 | 机制 | 归属 |
| --- | --- | --- |
| CI 测试（Actions） | GitHub 平台内原生触发 | GitHub 内部 |
| 后端部署（Render） | GitHub App 账号级 webhook | Render 服务器 |
| 前端部署（Cloudflare） | GitHub App 账号级 webhook | Cloudflare 服务器 |
| 文档站（gh-pages） | GitHub Actions `mkdocs gh-deploy` | GitHub 内部 |

**一句话**：不是"把 webhook 配到仓库"，而是"GitHub 通过 App 集成把 push 事件推给 Render/Cloudflare 的服务器"，它们在云端接收后自动构建部署——你只管 push，剩下全自动。

### 9.8 CI/CD 常见问题（FAQ）

**Q1：push 后多久上线？**
- CI：50 秒左右（74 测试 + 前端构建）
- Render 后端：2~5 分钟（Docker 构建）
- Cloudflare 前端：1~2 分钟
- 文档站：约 1 分钟（含 GitHub Pages 构建）

**Q2：CI 失败会怎样？**
- 会显示红色 ✗，PR 被拦截；但**不会阻止** Render/Cloudflare 部署（部署走独立 webhook 链路，与 Actions 无关）——如需"测试通过才部署"，可给 Render/Cloudflare 配 CI 状态门禁（高级话题）。

**Q3：怎么看 CI/CD 是否成功？**
- Actions 页面：https://github.com/davidyshuang/fund-trade-system/actions
- 每个 commit 旁边的 ✓/✗ 标识

**Q4：如何手动触发？**
- CI / Docs：Actions 页面 → 选 workflow → **Run workflow**（docs.yml 已开启 `workflow_dispatch`）
- Render / Cloudflare：各自 Dashboard 的 **Deploy / Redeploy** 按钮

## 十、注意事项与常见问题

| 问题 | 说明 |
|---|---|
| Render 免费实例休眠 | 15 分钟无访问自动休眠，下次首个请求冷启动 30~60 秒（正常） |
| Neon 计算节点休眠 | 免费层自动休眠，首次连接慢/失败可到 Neon SQL Editor 执行 `SELECT 1;` 唤醒 |
| 国内直连 Neon 失败 | 国内网络访问美国 PG 端口会被干扰（SSLRequest 即断连），属正常；Render（美国）连 Neon 不受影响 |
| 链接公开 | 部署后任何人拿到链接可访问演示数据，**勿录入真实资金/隐私数据** |
| 密码安全 | Neon 密码若泄露，到 Neon **Reset password** 轮换，并同步更新 Render `DB_PASSWORD` |
| 端口 | Render 通过 `PORT` 环境变量注入（Dockerfile 已处理 `${PORT:-8080}`） |

**已踩过的坑（避免重蹈）：**
1. Render 无原生 Java runtime → 必须 Docker 部署
2. Neon 强制 SSL → JDBC URL 必须 `sslmode=require`
3. Render 服务实际域名看日志「primary URL」，不是想当然的地址
4. Cloudflare Pages 的 Root directory 必须 `frontend`（子目录）
5. Build command 与 Output directory 是**两个字段**，勿拼在一起
6. 新版 Cloudflare UI 的 Deploy command 必填 → `npx wrangler deploy` + `[assets]` 语法

## 十一、一键本地开发（不影响线上）

```bash
# 后端（连本机 PostgreSQL）
cd backend && mvn spring-boot:run --server.port=8080
# 前端（Vite 代理到 8080）
cd frontend && npm run dev   # http://localhost:5173
```
