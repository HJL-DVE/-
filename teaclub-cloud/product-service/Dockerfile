# ============================================================
# TeaClub 微服务镜像（多阶段构建）
#
# 构建上下文必须是 teaclub-cloud 目录（需要父 pom.xml 与所有模块）：
#   docker build -f teaclub-cloud/Dockerfile \
#     -t teaclub/product-service:1.0.0 teaclub-cloud
#
# 服务通过 -Dservice.build=product-service 指定要打包的模块。
# ============================================================

# ---- 阶段 1：构建 ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

# 先只拷贝 pom，命中依赖层缓存
COPY pom.xml ./
COPY common ./common
COPY gateway-service ./gateway-service
COPY auth-service ./auth-service
COPY member-service ./member-service
COPY product-service ./product-service
COPY wallet-service ./wallet-service
COPY order-service ./order-service
COPY payment-service ./payment-service
COPY marketing-service ./marketing-service
COPY inventory-service ./inventory-service
COPY report-service ./report-service

# 打包目标模块及其依赖（-am 自动构建依赖的 common 模块）
ARG SERVICE_BUILD=product-service
RUN mvn -B -q -DskipTests -pl ${SERVICE_BUILD} -am clean package

# ---- 阶段 2：运行 ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai"

RUN groupadd -r teaclub && useradd -r -g teaclub teaclub

COPY --from=builder /build/product-service/target/*.jar /app/app.jar
RUN chown -R teaclub:teaclub /app
USER teaclub

EXPOSE 9003
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
