# 基金申购赎回管理系统 - GitHub Codespaces 开发容器
# 用途：推送到 GitHub 后，一键在云端打开完整开发环境（JDK21 + Maven + PostgreSQL），无需本地配置。
FROM mcr.microsoft.com/devcontainers/java:1-21-bullseye

# 安装 PostgreSQL（云端开发用，本机开发不受影响）
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql postgresql-contrib \
    && rm -rf /var/lib/apt/lists/*
