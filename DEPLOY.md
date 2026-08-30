# 部署指南（免费方案）

架构：**前端 Cloudflare Pages（免费） + 后端 Render 免费实例（Java 21） + 数据库 Neon 免费 PostgreSQL**

```
浏览器 → Cloudflare Pages（Vue 静态）→ API 请求 → Render（SpringBoot）→ Neon PostgreSQL
```

---

## 第 0 步：准备 GitHub 仓库（已完成）

代码已在 https://github.com/davidyshuang/fund-trade-system （私有仓库，main 分支）。

---

## 第 1 步：创建 Neon 免费数据库（约 3 分钟）

1. 打开 https://neon.tech → GitHub 登录（免费）
2. 创建项目，区域选 `Singapore (Southeast Asia)`（离国内更近）
3. 创建成功后复制 **连接串**（Connection string）：
   ```
   postgresql://fund_trade_owner:xxxxx@ep-xxxxx.ap-southeast-1.aws.neon.tech/fund_trade?sslmode=require
   ```
4. 在 Neon 控制台执行一条 SQL（建业务库，可选，默认库名即 `fund_trade`）：
   ```sql
   CREATE DATABASE fund_trade;
   ```
   也可直接用默认 `neondb`，只需把连接串里的库名改成 `fund_trade`。

> ⚠️ 记住连接串中的 5 个值：`host`（ep-xxxxx...neon.tech）、`port`（5432）、`db`（fund_trade）、`user`、`password`。

---

## 第 2 步：部署后端到 Render（约 5 分钟）

1. 打开 https://render.com → 用 GitHub 注册（免费）
2. 点 **New +** → **Blueprint** → 选择仓库 `fund-trade-system`
   （Render 会自动读取根目录的 `render.yaml`）
3. 确认服务 `fund-trade-backend`（free 实例、oregon 区域）→ **Apply**
4. 首次部署会自动构建。**部署完成后**：
   - 进入服务 → **Environment** 页
   - 把第 1 步 Neon 的 5 个值填入：
     | 变量 | 值 |
     |---|---|
     | `DB_HOST` | Neon 的 host（ep-xxxxx...neon.tech） |
     | `DB_PORT` | `5432` |
     | `DB_NAME` | `fund_trade` |
     | `DB_USERNAME` | Neon 的 user |
     | `DB_PASSWORD` | Neon 的 password |
   - 点 **Save Changes**（会自动重新部署）
5. 等部署完成后，打开服务地址（形如 `https://fund-trade-system.onrender.com`），访问 `/api/products` 应返回产品 JSON。
   > 💡 首次访问免费实例需要冷启动 30~60 秒，请耐心等待。
   > 📌 记下后端地址，下一步要用（形如 `https://fund-trade-system.onrender.com`）。

---

## 第 3 步：部署前端到 Cloudflare Pages（约 5 分钟）

1. 打开 https://dash.cloudflare.com → 注册（免费）→ 左侧 **Workers & Pages**
2. 点 **Create** → **Pages** → **Connect to Git** → 授权并选择 `fund-trade-system`
3. 构建配置：
   - **Framework preset**：`Vite`
   - **Build command**：`npm run build`
   - **Build output directory**：`dist`
   - **Root directory**：`frontend`
4. 展开 **Environment variables**，添加：
   | 变量 | 值 |
   |---|---|
   | `VITE_API_BASE` | 第 2 步的后端地址，如 `https://fund-trade-system.onrender.com`（**不带**末尾斜杠） |
5. 点 **Save and Deploy**
6. 部署完成后访问 `https://<项目名>.pages.dev`，即为你的基金管理系统前端。

---

## 完成！效果与注意点

- ✅ 手机/电脑任何设备访问前端地址即可使用；后端 + 数据库全在线。
- ⚠️ **Render 免费实例 15 分钟无访问会休眠**，再次打开前端操作时第一个请求要等 30~60 秒冷启动（属正常现象）。
- ⚠️ 部署后链接是**公开可访问**的（任何人拿到链接都能打开并操作 C001 等演示账户数据）——仅适合演示，不要录入真实资金/隐私数据。
- 🔄 后续改代码 `git push` 到 main，两端都会自动重新部署（Cloudflare 连 GitHub / Render autoDeploy）。

---

## 本地开发（不受影响）

```bash
# 后端（连本机 PostgreSQL）
cd backend && java -jar target/fund-trade-backend-0.1.0-SNAPSHOT.jar --server.port=8080
# 前端（走 Vite 代理）
cd frontend && npm run dev   # http://localhost:5173
```
