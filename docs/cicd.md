# CI/CD 持续集成与持续部署

> 本文档说明本仓库 CI/CD 的完整实现：GitHub Actions 质量门禁、Render/Cloudflare 自动部署、GitHub Pages 文档发布，以及"外部平台如何感知新 commit"的机制揭秘。

## 一、总览：一次 `git push` 触发什么

```
git push origin main
    │
    ├─▶ 🔵 GitHub Actions「CI」        → 质量门禁（74 测试 + 前端构建）
    ├─▶ 🟢 Render 自动部署（后端）     → autoDeploy 检测到 push
    ├─▶ 🟠 Cloudflare 自动部署（前端） → Git 集成检测到 push
    └─▶ 🔴 GitHub Actions「Docs」      → 仅 docs/ 变更时触发 → github.io 自动更新
```

四条链路全部**自动触发、零人工干预**。

---

## 二、CI（持续集成）— `.github/workflows/ci.yml`

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

---

## 三、CD 链路 1：后端 → Render

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

---

## 四、CD 链路 2：前端 → Cloudflare Pages

**原理**：Cloudflare Pages 的 **Git 集成**（连接 GitHub 仓库），push 后自动构建：

```
push → Cloudflare 拉代码 → cd frontend → npm ci && npm run build → 上传 dist/ → 发布
```

**关键配置**（Cloudflare Dashboard 上填写）：

| 字段 | 值 |
| --- | --- |
| Root directory | `frontend`（子目录！） |
| Build command | `npm run build` |
| Build output directory | `dist` |
| Build variables | `VITE_API_BASE = https://fund-trade-system.onrender.com` |
| Deploy command | `npx wrangler deploy`（配合 `frontend/wrangler.toml` 的 `[assets]` 语法） |

> 构建产物哈希变化即新版本（如 `index-CgoIaJM9.js`），可通过 assets 文件名判断是否已更新。

---

## 五、CD 链路 3：文档站 → GitHub Pages — `.github/workflows/docs.yml`

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

---

## 六、触发矩阵（改什么 → 触发什么）

| 改了什么 | CI 测试 | Render 后端 | Cloudflare 前端 | 文档站 |
| --- | --- | --- | --- | --- |
| `backend/**` | ✅ | ✅ | — | — |
| `frontend/**` | ✅ | — | ✅ | — |
| `docs/**` 或 `mkdocs.yml` | ✅ | — | — | ✅ |
| `Dockerfile`/`render.yaml` | ✅ | ✅ | — | — |
| 其他（README 等） | ✅（保守全量） | — | — | — |

> CI 目前对所有 push 全量运行（未做 paths 过滤），是**保守策略**；如需提速可给 `ci.yml` 增加 `paths`。

---

## 七、自动触发机制揭秘：Webhook vs GitHub App

> 常见疑问：GitHub 仓库并没有配置 webhook，Render/Cloudflare 是怎么知道有新 commit 的？

### 事实核查结果

- 仓库 `Settings → Webhooks` 列表为 **空**（`gh api .../hooks` 返回 `[]`），没有传统 Repository Webhook
- 但部署确实每次 push 自动触发

### 真相：靠「GitHub App 集成」，不是传统 webhook

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

### 两种机制对比

| 机制 | 在哪里注册 | 仓库 Webhooks 列表能看到吗 |
| --- | --- | --- |
| 传统 Repository Webhook | 每个仓库 `Settings → Webhooks` | ✅ 能看到（URL 形如 `api.render.com/v1/webhooks/...`） |
| **GitHub App 集成**（Render/Cloudflare 所用） | **账号级**安装，App 服务器注册全局端点 | ❌ 看不到（GitHub 把事件推给 App 自身，不在仓库 hooks 里） |

### 如何在网页验证

1. 仓库 → **Settings → Integrations**：`https://github.com/<owner>/<repo>/settings/installations`
2. 或账号头像 → **Settings → Applications → Installed GitHub Apps**
3. 应能看到 **Render** 与 **Cloudflare** 已安装且关联本仓库

### 四条触发链路归属总结

| 触发链路 | 机制 | 归属 |
| --- | --- | --- |
| CI 测试（Actions） | GitHub 平台内原生触发 | GitHub 内部 |
| 后端部署（Render） | GitHub App 账号级 webhook | Render 服务器 |
| 前端部署（Cloudflare） | GitHub App 账号级 webhook | Cloudflare 服务器 |
| 文档站（gh-pages） | GitHub Actions `mkdocs gh-deploy` | GitHub 内部 |

**一句话**：不是"把 webhook 配到仓库"，而是"GitHub 通过 App 集成把 push 事件推给 Render/Cloudflare 的服务器"，它们在云端接收后自动构建部署——你只管 push，剩下全自动。

---

## 八、常见问题（FAQ）

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
