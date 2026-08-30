#!/bin/bash
# Codespaces 容器创建后执行：启动 PostgreSQL、设置密码、创建业务库与测试库
set -e

# 启动 PostgreSQL 服务
service postgresql start

# 设置 postgres 用户密码（与 devcontainer.json 的 containerEnv 保持一致）
sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'postgres';"

# 创建业务库与测试库（已存在则跳过）
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='fund_trade'" | grep -q 1 \
    || sudo -u postgres createdb fund_trade
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='fund_trade_test'" | grep -q 1 \
    || sudo -u postgres createdb fund_trade_test

echo "✅ PostgreSQL 就绪：fund_trade / fund_trade_test"
echo "启动后端：cd backend && mvn spring-boot:run"
echo "启动前端：cd frontend && npm install && npm run dev"
