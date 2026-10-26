# ============================================================================
# 应用镜像：多阶段构建（Maven 编译 -> JRE 运行）
#
# 一个 Dockerfile 服务三个模块，用构建参数 MODULE 区分，避免三份几乎相同的文件。
#
# 构建上下文必须是「仓库根目录」：
#   docker build -f docker/app/Dockerfile --build-arg MODULE=mini-user-service \
#                -t mini-user-service:local .
# 推荐直接用 docker/app/docker-compose.yml（参数都已写好）：
#   docker compose -f docker/app/docker-compose.yml build
# ============================================================================

# ---------------------------------------------------------------- 构建阶段
# 用 temurin 8 构建：项目 java.version=1.8，产物是 Java 8 字节码，与运行阶段的 JRE 8 对齐
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

# 先只拷 pom：只要依赖没变，这一层就命中缓存，改源码不会触发重新下载依赖。
# （pom 变了才会重下，这是把依赖解析单独放一层的意义）
COPY pom.xml ./
COPY mini-common/pom.xml mini-common/
COPY mini-user-service/pom.xml mini-user-service/
COPY mini-order-service/pom.xml mini-order-service/
COPY mini-gateway/pom.xml mini-gateway/
RUN mvn -B -ntp -DskipTests dependency:go-offline

# 再拷源码，只构建目标模块（-am 会连带构建它依赖的 mini-common）；
# .dockerignore 已排除 target/，避免把宿主上编译的旧产物带进来
COPY . .
ARG MODULE
RUN mvn -B -ntp -DskipTests -pl "${MODULE}" -am package

# ---------------------------------------------------------------- 运行阶段
FROM eclipse-temurin:8-jre
ENV TZ=Asia/Shanghai
WORKDIR /app

ARG MODULE
COPY --from=build /build/${MODULE}/target/*.jar /app/app.jar

# 非 root 运行：eclipse-temurin 镜像里只有 root，这里自建一个用户
RUN groupadd -r app && useradd -r -g app -d /app app && chown -R app:app /app
USER app

# 端口由 compose 注入的 SERVER_PORT 决定（应用侧读的是 ${SERVER_PORT:...}）。
# 这里刻意不写 EXPOSE：EXPOSE 是构建期常量，写死一个端口只会在 docker compose ps
# 里显示成 "8080/tcp" 这种与实际监听端口不符的信息，反而误导。
ENV SERVER_PORT=8080

# 健康检查直接打 actuator（eclipse-temurin:8-jre 自带 curl，无需额外装包）。
# start-period 给足 Spring Boot 启动时间，否则刚起就被判定不健康，
# 会连累 compose 里 condition: service_healthy 的下游服务一直等。
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=12 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

# 用 exec 让 java 顶替 sh 成为 1 号进程：容器收到 SIGTERM 时信号能直达 JVM，实现优雅停机。
# JAVA_OPTS 用于按模块调堆大小（compose 里注入）。
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]