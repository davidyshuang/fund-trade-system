# 基金申购赎回管理系统

> 一套严格遵循证券基金交易业务规则的基金申购、赎回、订单查询与持仓查询管理系统。

## 系统简介

| 项 | 内容 |
| --- | --- |
| 定位 | 前后端分离的基金交易管理系统（演示/教学级，含完整业务规则） |
| 核心功能 | 基金申购、基金赎回、订单查询、持仓查询、管理端（产品/净值/交易日历） |
| 业务规则 | 交易日历、15:00 截单、T+1 份额确认、资金冻结、TA 账户份额管理 |
| 技术栈 | Vue3 + Element Plus / Java JDK21 + Spring Boot 3.x + DDD 四层 / PostgreSQL |
| 开发模式 | TDD（红→绿→重构），74 个自动化测试全绿 |

## 文档导航

| 类别 | 文档 | 说明 |
| --- | --- | --- |
| 需求 | [创意愿景](01-idea.md) | 一句话愿景、目标用户、核心价值 |
| 需求 | [业务需求文档 BRD](02-brd.md) | 业务背景、用户故事地图、GIVEN/WHEN/THEN 业务场景、业务流程 |
| 需求 | [产品需求文档 PRD](03-prd.md) | 功能需求、REST API 契约、验收标准 |
| 技术 | [系统设计](04-design.md) | 架构、DDD 领域模型、领域事件、分层规范、TDD 策略 |
| 技术 | [架构决策 ADR](05-adr.md) | 关键架构决策与理由（含踩坑记录） |
| 交付 | [部署与 CI/CD 指南](06-deployment.md) | 从零开始免费部署 + 自动发布机制（Cloudflare + Render + Neon + GitHub Actions） |
| 交付 | [使用指南](07-user-guide.md) | 面向用户的系统操作手册 |

## 在线体验

| 组件 | 地址 |
| --- | --- |
| 前端页面 | https://fund-trade-system.931655086.workers.dev |
| 后端 API | https://fund-trade-system.onrender.com/api/products |
| 代码仓库 | https://github.com/davidyshuang/fund-trade-system |

> 演示系统公开可访问，请勿录入真实资金/隐私数据。
