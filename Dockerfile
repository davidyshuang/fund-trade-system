# ============================================================
# 基金申购赎回系统后端 - 多阶段构建
# 阶段 1：Maven 构建（JDK 21）
# 阶段 2：JRE 21 运行（精简镜像）
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先拷贝 pom 并预下载依赖（利用层缓存加速重复构建）
COPY backend/pom.xml .
RUN mvn -B dependency:go-offline

# 拷贝源码并打包（跳过测试，测试由 CI 负责）
COPY backend/src ./src
RUN mvn -B package -DskipTests

# ── 运行阶段 ──
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/fund-trade-backend-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
# 监听 PORT 环境变量（Render 注入），默认 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
