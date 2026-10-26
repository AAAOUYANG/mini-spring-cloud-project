# mini-spring-cloud

一个可以照着跑起来的 **Spring Cloud 微服务骨架**：`Gateway` 统一入口、`Nacos` 做注册中心与配置中心、业务服务之间用 `OpenFeign` 互调、`MySQL` 存业务数据、`Jenkins` 做本地 CI。基础设施与应用都能用 Docker 一键起，也支持「基础设施在容器、应用在 IDE」的混合模式。

业务代码沿用了单模块时代的风格：分层（`controller / service / repository / domain`）、MyBatis-Plus 管分页与逻辑删除、**查询 SQL 全部写在 Mapper XML**（便于改连表）、统一响应 + 全局异常处理、MapStruct 做对象转换，另带一个项目内的 MyBatis-Plus 代码生成器。

## 模块与技术栈

| 模块 | 职责 | 说明 |
| --- | --- | --- |
| `mini-common` | 共享模型 | `ApiResponse`、`PageResult`（包名 `com.example.common.web`）。**服务间只共享这里的东西**，业务 DTO 各自持有 |
| `mini-user-service` | 用户服务 | 连 MySQL，走 MyBatis-Plus，保留生成器产物与分层结构（包名 `com.example.demo`） |
| `mini-order-service` | 订单服务 | 不连库，通过 `OpenFeign` 调用户服务，演示服务间调用 |
| `mini-gateway` | API 网关 | Spring Cloud Gateway（WebFlux），按服务名转发 |

| 组件 | 版本 / 说明 |
| --- | --- |
| JDK | 1.8（构建与运行镜像都是 8；本机用 17 跑也没问题） |
| Spring Boot | 2.7.18（最后一个支持 Java 8 的 2.x 版本） |
| Spring Cloud | 2021.0.9（2021.0.x 最后一版，支持 Boot 2.6/2.7） |
| Spring Cloud Alibaba | 2021.0.6.2 |
| Spring Cloud Gateway | 3.1.9（**WebFlux 栈，模块里绝不能出现 `spring-boot-starter-web`**） |
| OpenFeign | `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer`（2021 起 Ribbon 已移除，`lb://` 必须显式引入 loadbalancer） |
| Nacos | 服务端 **v2.4.3**；客户端用 SCA 自带的 **2.2.0**（见下「版本对齐」） |
| MySQL | 8.0+，驱动 `com.mysql:mysql-connector-j` |
| MyBatis-Plus | 3.5.7；分页用 `PaginationInnerInterceptor`，不用 PageHelper |
| MapStruct / Lombok | 1.5.5.Final + `lombok-mapstruct-binding` 0.2.0；Lombok 版本由 Boot 管理 |
| 代码生成器 | `com.baomidou:mybatis-plus-generator` 3.5.7 + Velocity 2.3 |
| Maven | 3.9.9，通过 Wrapper（`./mvnw`），无需本机安装 |

### 版本对齐：为什么不把 nacos-client 锁到 2.4.3

直觉上客户端应与服务端同版本，但**这里刻意不对齐**。比对两边 POM 的实测结论：

| 构件 | 是否依赖独立的 `nacos-api` |
| --- | --- |
| `nacos-client:2.2.0` | **是**（`nacos-api` 是独立构件） |
| `nacos-client:2.4.3` | **否**（2.4.x 已把 `nacos-api` 合并进 `nacos-client`） |

也就是说锁定 2.4.3 不是「升个小版本」，而是换掉了一条依赖结构，而 SCA 2021.0.6.2 并不是针对它编译的。Nacos 2.x 服务端向下兼容 2.x 客户端，所以这里用 SCA 自带的 2.2.0，并用运行时实测背书：**2.2.0 客户端能正常注册到 2.4.3 服务端、能拉取配置、能动态刷新**。

## 目录结构

```text
.
├── pom.xml                       # 聚合父 pom（dependencyManagement 统一管理版本）
├── mvnw / mvnw.cmd / .mvn        # Maven Wrapper
├── .dockerignore                 # 构建上下文排除（顺带避免把 .env 烤进镜像层）
├── mini-common/                  # 共享模型：ApiResponse、PageResult
├── mini-user-service/            # 用户服务（连库、生成器、分层结构都在这里）
│   └── src/main/java/com/example/demo
│       ├── MiniSpringBootApplication.java
│       ├── config                # MybatisPlusConfig、PasswordConfig、MyBatisMetaObjectHandler
│       ├── exception             # BusinessException、GlobalExceptionHandler
│       ├── generator             # CodeGenerator
│       ├── controller/UserController.java、ConfigDemoController.java
│       ├── service|repository|convert|domain
│       └── ...                   # 生成器产物见「代码生成器」一节
├── mini-order-service/           # 订单服务：OrderController + UserClient(Feign) + OrderView/UserView
├── mini-gateway/                 # 网关：GatewayApplication + application.yml 里的路由
└── docker
    ├── infra/                    # 基础设施层：MySQL + Nacos + Jenkins（+ 配置发布任务）
    │   ├── docker-compose.yml
    │   ├── nacos/config/*.yaml   # 配置中心基础层（所有环境共用），随仓库管理
    │   ├── nacos/config/env/<环境>/mini-env.yaml   # 配置中心环境层（dev / prod 各一份）
    │   ├── nacos/publish-config.sh
    │   ├── mysql/init/*.sql
    │   └── jenkins/
    └── app/                      # 应用层：三个微服务的镜像与编排
        ├── Dockerfile            # 多阶段构建，用 MODULE 参数区分模块
        ├── env/<环境>.env        # 环境差异：Nacos 命名空间、数据源、镜像 tag
        └── docker-compose.yml
```

## 服务与端口

| 服务 | 混合模式端口 | 容器化模式 | 账号 | 说明 |
| --- | --- | --- | --- | --- |
| `mini-gateway` | 9000 | 宿主 9000 → 容器 9000 | 无 | 唯一入口 |
| `mini-user-service` | 8081 | 宿主 18081 → 容器 8081 | 无 | 端口故意错开，两种模式可同时存在而不抢端口 |
| `mini-order-service` | 8082 | 宿主 18082 → 容器 8082 | 无 | |
| MySQL | 13306 | 容器 3306 | `root` / `root` | 宿主 3306 已被本机原生 MySQL 占用 |
| Nacos | 8848 / 9848 / 9849 | 同宿主 | 未开鉴权（打开即用） | 9848/9849 是 Nacos 2.x 的 gRPC 端口，**必须映射**，否则宿主机上的应用注册不上 |
| Jenkins | 8080 / 50000 | 同宿主 | 未开鉴权（打开即用） | |
| Portainer | — | 宿主 9001 → 容器 9000 | `admin` / `MiniPortainer2026` | 容器可视化：用网页查看/管理本机所有容器（含日志、环境变量、端口映射）。容器内固定 9000，宿主 9000 已被网关占用故错开到 9001；同样只绑 `127.0.0.1` |

> 以上账号只适用于本地测试环境：所有端口都只绑 `127.0.0.1`，不对外暴露。Portainer 的密码登录后可在 **My account** 里改；忘了就 `cd docker/infra && docker compose down && docker volume rm mini-portainer-data` 再起，会回到「创建管理员」那一步。

## 运行方式

两种模式**二选一**，不要同时跑：它们注册的服务名相同，会变成「同一个服务名下两个实例」，网关会轮询到宿主与容器两边，排查问题时非常容易困惑。

### 方式 A：混合模式（基础设施在容器，应用在 IDE/本机）

适合改代码后快速重启的场景。

```bash
# 1. 起基础设施（MySQL + Nacos + Jenkins + Portainer，并自动发布配置中心的内容）
cd docker/infra
cp .env.example .env      # 首次：把 WORKSPACE_DIR 改成仓库根目录绝对路径（pwd 获取）
docker compose up -d
cd ../..

# 2. 首次先 install 一次：把共享模块 mini-common 装进本地仓库
./mvnw install -DskipTests

# 3. 起三个应用（三个终端，或 IDE 里直接 Run）
#    本机直跑必须覆盖数据源：dev 的库地址在配置中心里指向容器网络（mysql:3306），
#    环境变量优先级高于配置中心，这里临时换成宿主端口 13306
#   （IDE 里 Run 时，把这行加到 Run Configuration 的环境变量里）
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13306/mini_spring_boot?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true'
./mvnw -pl mini-user-service  spring-boot:run
./mvnw -pl mini-order-service spring-boot:run
./mvnw -pl mini-gateway       spring-boot:run
```

第 2 步不能省：`spring-boot:run` 是**插件目标**而不是生命周期阶段，`-am` 对它不生效，所以不先把 `mini-common` 装进本地仓库的话，单模块启动会报 `Could not find artifact com.example:mini-common:jar:0.0.1-SNAPSHOT`。（IDE 里直接 Run 不依赖这一步，IDE 会按工作区解析模块依赖。`./mvnw clean test`、`./mvnw clean package` 这类**全 reactor** 命令也不需要。）

应用默认连 `127.0.0.1:8848`（Nacos）。MySQL 地址**由 Nacos 的 `dev` 环境层决定**（那里写的是容器网络的
`mysql:3306`），所以本机直跑要用上面那个 `SPRING_DATASOURCE_URL` 覆盖成 `127.0.0.1:13306`——环境变量优先级高于配置中心。
默认读写 Nacos 的 **`dev` 命名空间**（`NACOS_NAMESPACE` 的默认值就是 `dev`）；要连生产配置必须显式 `NACOS_NAMESPACE=prod`，见「多环境隔离」。

### 方式 B：全容器化

```bash
cd docker/infra && docker compose up -d    # 1. 基础设施必须先起（应用要连它的网络与配置中心）
                                           #    附带 Portainer 容器可视化：http://127.0.0.1:9001
cd ../..

# 2. 起应用：APP_ENV / IMAGE_TAG 必须显式给 —— env 文件的路径与 image 的 tag 都靠它们，
#    而 compose 的变量替换读不到 env 文件的内容。漏传会被 :? 守卫直接拦下，不会悄悄用错环境。
APP_ENV=dev IMAGE_TAG=local docker compose -f docker/app/docker-compose.yml up -d --build
APP_ENV=dev IMAGE_TAG=local docker compose -f docker/app/docker-compose.yml ps   # 三个应用应显示 healthy

curl 'http://127.0.0.1:9000/api/order/1001?userId=1'   # 网关 -> 订单 -> Feign -> 用户
```

容器里的应用连的是 `nacos:8848` 与 `mysql:3306`（compose 内置 DNS 直接解析服务名）；环境差异（Nacos 命名空间、数据源、镜像 tag）集中在 `docker/app/env/<环境>.env`，由 compose 的 `env_file` 注入容器，代码与配置中心都不用改。切环境就是换一个 `APP_ENV` 重建容器，见「多环境隔离」。

镜像构建要点：一个 `Dockerfile` 用 `--build-arg MODULE=xxx` 服务三个模块；先把各个 `pom.xml` 单独拷一层并跑 `dependency:go-offline`，这样改源码不会触发重新下载依赖；运行阶段用 `eclipse-temurin:8-jre`（自带 `curl`，健康检查直接打 `/actuator/health`），以非 root 用户运行，入口用 `exec java` 让 JVM 成为 1 号进程以便优雅停机。

### 打通链路

```bash
# 建一个用户（直连用户服务）
curl -X POST http://127.0.0.1:8081/user/save \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123","nickname":"Alice","status":1}'

# 经网关查用户（Path=/api/user/** + StripPrefix=1 → /user/**）
curl http://127.0.0.1:9000/api/user/1

# 经网关下订单：网关 -> 订单服务 -> OpenFeign -> 用户服务
curl 'http://127.0.0.1:9000/api/order/1001?userId=1'
```

## 多环境隔离（dev / prod）

**镜像只有一个，环境差异全部在运行时注入。** 这是刻意的：dev 验过的镜像和 prod 跑的必须是
同一个东西（见下面「晋级」）。区分维度一共四个，都在配置与部署侧，不在代码里：

| 维度 | dev | prod | 靠什么区分 |
| --- | --- | --- | --- |
| 镜像 | `<模块>:<短SHA>` | **同一个** `<模块>:<短SHA>` | 无差异——同一制品晋级成 `:prod` 指针 |
| 配置中心 | Nacos 命名空间 `dev` | Nacos 命名空间 `prod` | `NACOS_NAMESPACE`（默认 `dev`） |
| 注册中心 | 命名空间 `dev` | 命名空间 `prod` | 同上——**dev 的网关看不到 prod 的实例** |
| 数据源 | `mini_spring_boot` | `mini_spring_boot_prod` | 各命名空间里的 `mini-env.yaml` |
| 容器环境变量 | `docker/app/env/dev.env` | `docker/app/env/prod.env` | `APP_ENV` 决定用哪个文件 |
| 流水线 | `mini-spring-cloud-dev` | `mini-spring-cloud-prod` | 任务名（不是参数） |

**默认永远是 dev**：`NACOS_NAMESPACE` 的默认值就是 `dev`，所以「不显式指定就永远读不到生产
配置」——生产配置只可能被显式注入。这也是为什么它默认不是空值。

### 配置怎么分层

| 层 | 文件 | 发布到 |
| --- | --- | --- |
| 基础层 | `docker/infra/nacos/config/*.yaml` | 每个环境的命名空间各发一份（内容相同） |
| 环境层 | `docker/infra/nacos/config/env/<环境>/mini-env.yaml` | dataId `mini-env.yaml`，各环境内容不同 |

`spring.config.import` 把环境层放在**最后**，同名键以它为准：

```yaml
- optional:nacos:mini-common.yaml
- optional:nacos:mini-user-service.yaml
- optional:nacos:mini-env.yaml        # 环境层，优先级最高
```

刻意**不用**「同名 dataId 用环境层整文件覆盖」：那样一旦环境层写漏一段，基础层配置会被
**静默丢掉**，而且在配置中心里还看不出来。独立 dataId 只描述差异，没有这个风险。

一键重放（幂等，起基础设施时会由 `nacos-config-init` 自动调用）：

```bash
sh docker/infra/nacos/publish-config.sh                    # 发布所有环境（默认 dev prod）
ENVIRONMENTS=dev sh docker/infra/nacos/publish-config.sh   # 只发 dev
```

### 怎么确认隔离真的生效

同一个 dataId 在两个命名空间读出不同内容，是最直接的证据。项目里留了个探针：
`GET /config/demo` 返回环境层里的 `mini.demo.message`。

```bash
docker exec mini-user-service curl -s http://127.0.0.1:8081/config/demo
# dev  -> {"code":0,"message":"ok","data":"hello from Nacos config center [dev]"}
# prod -> {"code":0,"message":"ok","data":"hello from Nacos config center [prod]"}
```

流水线的「部署与冒烟」阶段会**断言**这个值必须含 `[dev]`：接错环境直接失败，不会出现
「以为是 dev、实际连了生产配置」这种事。

### 刻意还没做的部分

- **prod 不部署**：本机没有第二台机器/集群、也没有镜像仓库，所以 prod 流水线只把镜像晋级成
  `:prod` 指针并打印发布命令，不执行。真要发布是
  `APP_ENV=prod IMAGE_TAG=prod docker compose -f docker/app/docker-compose.yml up -d`
- **prod 库是空的**：`mini_spring_boot_prod` 由 prod 实例按环境层配置自动创建并建表，但没有种子
  数据（dev 库里的 alice/bob 是开发阶段自己插的）。所以用 prod 环境起本地容器时接口返回空，
  这是**预期行为**，不是隔离故障
- **生产凭据**：密码目前仍由 compose 的 `MYSQL_ROOT_PASSWORD` 兜底；真实生产应走 Jenkins 凭据 /
  密钥管理注入

## 配置分层

配置按「变化频率」分三层，这是本项目最需要先理解的一点：

| 层 | 放什么 | 位置 |
| --- | --- | --- |
| 引导层 | 端口、应用名、Nacos 地址、`spring.config.import` | 各模块本地 `application.yml` |
| 动态层 | 数据源、Feign 超时、Jackson、日志级别、业务开关 | **Nacos 配置中心**（`docker/infra/nacos/config/*.yaml` = 基础层，所有环境共用） |
| 环境层 | dev/prod 不一致的值（数据源、日志级别、环境标识） | **Nacos 配置中心**（`config/env/<环境>/mini-env.yaml`），导入顺序最后 → 优先级最高，见「多环境隔离」 |
| 结构层 | 网关路由、MyBatis `mapper-locations` | 本地（和代码/资源文件绑定，改配置中心反而容易不一致） |
| 环境差异 | `NACOS_ADDR`、`NACOS_NAMESPACE`、`SERVER_PORT` 等 | **环境变量**（容器、CI 用）。数据源地址刻意不在此层，见下 |

**优先级是实测出来的，不是猜的**（`/actuator/env` 的属性源顺序）：

```text
命令行参数 > 系统属性 > 环境变量 > Nacos 配置 > 本地 application.yml（最低）
```

即：**Nacos 能覆盖本地 yml，环境变量能覆盖 Nacos**。所以同一个镜像不用改代码就能在不同环境跑，而本地调试想临时改点东西时，改本地 yml 是**不会**生效的（它优先级最低），要用环境变量或命令行参数。

**但数据源地址刻意不走环境变量**（dev/prod 都由环境层的 `mini-env.yaml` 给字面值）。踩过的坑：容器里曾用
`DB_URL` 注入，而配置里写的是 `url: ${DB_URL:jdbc:mysql://127.0.0.1:13306/…}` —— 于是在 Nacos 控制台改冒号
后面那个默认值**毫无效果**，因为占位符永远先命中环境变量，看起来就像"配置中心没生效"。现在：

- 改库 = 改配置中心那一行 → 保存发布 → **重启进程**；
  ⚠️ `docker compose up -d` 在服务定义没变时**不会重启容器**（只打印一行 `Container … Running`），
  必须 `docker compose restart mini-user-service`，否则改了也不会生效 —— 这是"以为配置没生效"的第二种成因；
- ⚠️⚠️ 第三种成因更危险：数据源**会被热刷新**。实测（2026-10-25）在控制台改完 url 约 1 秒后，用户服务日志出现
  `Refresh keys changed: [spring.datasource.url]`，而 `MiniHikariPool` **没有重新启动**，新连接已经建到了新库上。
  可 `spring.sql.init` 的 `init.sql` **只在启动时执行**，于是新库是空的：接口全部 500
  `Table '<新库>.sys_user' doesn't exist`，响应体还是空的，而健康检查一直是 UP、启动日志毫无异常。
  **换库务必重启进程**，并且只用下面这条判断"进程实际连的是哪个库"——它比控制台更可信：
  `docker exec mini-mysql mysql -uroot -proot -N -e "SELECT host, db FROM information_schema.processlist WHERE db IS NOT NULL"`；
- 本机直跑要连宿主端口时，用 `SPRING_DATASOURCE_URL` 临时覆盖（它是环境变量，优先级高于配置中心）；
- 少了环境层那份配置，启动会直接报 `Failed to configure a DataSource` —— 刻意的 fail-fast。

### 环境变量

| 变量 | 默认值 | 作用 |
| --- | --- | --- |
| `NACOS_ADDR` | `127.0.0.1:8848` | Nacos 地址（注册中心与配置中心共用） |
| `NACOS_NAMESPACE` | `dev` | **环境隔离的关键**：注册中心与配置中心都用这个命名空间。默认 `dev` 保证「不显式指定就永远读不到生产配置」；生产必须显式 `prod` |
| `APP_ENV` | 无（必填，仅容器化） | `docker compose` 用哪个环境文件（`docker/app/env/<值>.env`）；只给 shell 用，不进容器 |
| `IMAGE_TAG` | 无（必填，仅容器化） | `docker compose` 用哪个镜像 tag；只给 shell 用，不进容器 |
| `NACOS_DISCOVERY_IP` | 空 = 自动探测 | 指定注册到 Nacos 的本机 IP。留空时 SCA 自动探测（宿主上得到 LAN IP，容器里得到容器 IP）；**只有在自动探测选错网卡时才需要显式指定**，例如 `NACOS_DISCOVERY_IP=127.0.0.1`。容器里**绝不要**设成 `127.0.0.1`，网关会连不上 |
| `SERVER_PORT` | 8081 / 8082 / 9000 | 各服务端口 |
| `SPRING_DATASOURCE_URL` | 不设 | 数据源地址的临时逃生口（标准 Spring 变量，优先级高于配置中心）。默认由 Nacos 环境层决定，本机直跑时才需要设 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | 数据库账号 |
| `JAVA_OPTS` | 见 compose | 仅容器镜像用，传给 JVM 的启动参数 |
| `TZ` | `Asia/Shanghai` | 时区 |

## Nacos 配置中心

配置内容**随仓库管理**，不只躺在 Nacos 的数据库里——否则 `docker compose down -v` 一删就没了，新人 clone 下来也无从得知有哪些配置项。

| dataId | 内容 |
| --- | --- |
| `mini-common.yaml` | 三个服务共用：Jackson、根日志级别、演示参数 `mini.demo.message` |
| `mini-user-service.yaml` | 数据源、`spring.sql.init`、repository 日志级别 |
| `mini-order-service.yaml` | Feign 超时与日志级别 |
| `mini-gateway.yaml` | Gateway httpclient 超时、网关日志级别 |

发布方式（幂等，同名 dataId 覆盖）：

```bash
# 通常不用手动执行：docker compose up -d 时 nacos-config-init 一次性容器会自动发布
cd docker/infra
docker compose run --rm nacos-config-init     # 改了 nacos/config/*.yaml 后重新发布
```

服务端接入方式是 `spring.config.import`（不需要 `spring-cloud-starter-bootstrap`）：

```yaml
spring:
  config:
    import:
      - optional:nacos:mini-common.yaml
      - optional:nacos:mini-user-service.yaml
```

`optional:` 前缀让「配置中心里还没有这些配置」时不至于启动失败（生产环境建议去掉，让配置缺失直接暴露）。另外：引了 `spring-cloud-starter-alibaba-nacos-config` 却不写 `spring.config.import`，SCA 的 import-check 会直接让启动失败——这是刻意设计，避免「以为接了配置中心其实没接」。

### 动态刷新（已实测）

改动配置后**无需重启**，约 2 秒内自动生效，也不需要手动调用 `/actuator/refresh`（SCA 默认开启配置监听）：

```bash
# 看当前值（来自 mini-common.yaml 的 mini.demo.message）
curl http://127.0.0.1:8081/config/demo

# 在 Nacos 控制台改掉 mini.demo.message 后再请求，值会立刻变化
```

`/config/demo` 由 `ConfigDemoController`（`@RefreshScope`）提供，它存在的唯一目的就是把「配置中心确实在工作」变成可观测的事实；真实项目里应当删掉。想看某个配置项的来源，临时把 `env` 加进 `management.endpoints.web.exposure.include`，然后查 `/actuator/env/{key}`——注意它会回显配置值（包括数据库密码），只适合本地。

> ⚠️ **"能热刷新"不等于"热刷新安全"**：`spring.datasource.url` 这类配置会被就地重绑定到**活着的连接池**上（不需要重启进程），
> 但依赖启动期动作的东西——`init.sql` 建表、连接池预热、网关路由装配——**不会跟着重跑**。实测踩过一次：
> 控制台把 dev 的库名换成另一个空库 → 数据源热切换 → `Table '<新库>.sys_user' doesn't exist` → 全站 500，
> 而进程一直是 `healthy`。所以：**换库这类配置改完要重启**；只有 `mini.demo.message` 这种纯值型配置才适合依赖热刷新。

## 接口

### 用户服务（`mini-user-service`，前缀 `/user`）

| 方法 | 路由 | 入参 | 返回 |
| --- | --- | --- | --- |
| 新增 | `POST /user/save` | `@RequestBody UserDTO` | `ApiResponse<UserDTO>` |
| 修改 | `PUT /user/{id}` | 路径 `id` + `UserDTO` | `ApiResponse<UserDTO>` |
| 删除 | `DELETE /user/{id}` | 路径 `id` | `ApiResponse<Boolean>`（软删除，见下） |
| 详情 | `GET /user/{id}` | 路径 `id` | `ApiResponse<UserDTO>`；查不到时 `data` 为 `null`（被 `non_null` 省略） |
| 列表 | `GET /user/list` | `UserQueryVO`（GET 参数） | `ApiResponse<List<UserDTO>>` |
| 分页 | `GET /user/page` | `UserPageQueryVO`（`pageNum=1`、`pageSize=10`） | `ApiResponse<PageResult<UserDTO>>` |
| 配置演示 | `GET /config/demo` | — | `ApiResponse<String>`（Nacos 配置值，仅演示用） |

### 订单服务（`mini-order-service`，前缀 `/order`）

| 方法 | 路由 | 入参 | 返回 |
| --- | --- | --- | --- |
| 订单详情 | `GET /order/{orderId}?userId=1` | 路径 `orderId` + `userId` | `ApiResponse<OrderView>`，其中 `user` 字段是**通过 OpenFeign 从用户服务取回**的 |

订单数据是本地构造的（该模块不连库），重点在调用链路：`OrderController` → `UserClient`（`@FeignClient(name = "mini-user-service", path = "/user")`）→ 负载均衡解析实例 → 用户服务。

### 网关路由（`mini-gateway`）

| 路由 id | 断言 | 过滤器 | 转发到 |
| --- | --- | --- | --- |
| `mini-user-service` | `Path=/api/user/**` | `StripPrefix=1` | `lb://mini-user-service`（`/api/user/1` → `/user/1`） |
| `mini-order-service` | `Path=/api/order/**` | `StripPrefix=1` | `lb://mini-order-service` |

刻意没开 `spring.cloud.gateway.discovery.locator.enabled`：开了之后 `/mini-user-service/**` 这类按服务名的路由会自动暴露，服务名直接变成 URL 前缀。路由写在本地 `application.yml`（结构性配置），**动态改路由需要在改完后调 `POST /actuator/gateway/refresh`**。

响应统一是 `ApiResponse`，成功时 `code = 0`：

```json
{
  "code": 0,
  "message": "ok",
  "data": { "id": 1, "username": "alice", "nickname": "Alice", "status": 1,
            "createdAt": "2026-10-25 12:44:57", "updatedAt": "2026-10-25 12:44:57" }
}
```

- 分页接口的 `data` 是 `PageResult`：`records` / `total` / `pageNum` / `pageSize` / `pages`。
- `LocalDateTime` 固定按 `yyyy-MM-dd HH:mm:ss` 输出（DTO 上的 `@JsonFormat`），时区由 `spring.jackson.time-zone`（在 Nacos 的 `mini-common.yaml` 里）决定。
- `spring.jackson.default-property-inclusion: non_null`：值为 `null` 的字段**整个键都不输出**（只影响响应，不影响入库）。所以 `password`（`@JsonProperty(WRITE_ONLY)`）和库里为 `NULL` 的审计字段都不会出现在返回里。
- 失败响应的 `code` 等于 HTTP 状态码：`GlobalExceptionHandler` 处理 `BusinessException`、参数校验失败（400）、请求体不可读 / 参数类型不匹配（400），其他异常统一 500；未匹配到路由时仍是 Spring 默认的 404。当前生成的接口既没有校验注解、也没有抛 `BusinessException` 的地方，要启用校验就给 DTO/QueryVO 加 `@Valid` 和约束注解。
- `ApiResponse` 的私有构造器上标了 `@JsonCreator` / `@JsonProperty`：它的字段是 `final`、构造器私有，服务间用 Feign 调用时消费端要把响应体反序列化回来。当前构建恰好开了 `-parameters`（Boot 父 pom 默认），所以没有这两个注解也能跑；显式标注是为了**不依赖这个隐式前提**——一旦编译参数丢失，运行时会报 `cannot deserialize from Object value`，很难查。

## 测试

```bash
./mvnw test          # 全模块；也可以 ./mvnw -pl mini-user-service -am test
```

`mini-user-service` 的 [UserApiIntegrationTest](mini-user-service/src/test/java/com/example/demo/controller/UserApiIntegrationTest.java) 用 H2 的 MySQL 兼容模式跑全链路（controller → service → repository），覆盖增删改查、分页、逻辑删除、重复删除返回 `false`。

测试**不出网、不依赖任何外部组件**：`application-test.yml` 里关掉了 Nacos 的注册发现与配置客户端，并把地址指向必然连不上的 `127.0.0.1:1`——这样万一哪天「测试不打配置中心」的前提被破坏，测试会立刻失败，而不是悄悄连上本机正在运行的 Nacos 让人误以为隔离是好的。

## 打包与镜像

```bash
./mvnw clean package                 # 产出各模块 target/*.jar
java -jar mini-user-service/target/mini-user-service-0.0.1-SNAPSHOT.jar

# 容器镜像
docker compose -f docker/app/docker-compose.yml build
docker compose -f docker/app/docker-compose.yml up -d
```

## 本地 CI（Jenkins）

基础设施 compose 里已经带起了 Jenkins（<http://127.0.0.1:8080>，只绑 127.0.0.1、无鉴权）。**四个**流水线任务由 JCasC 自动创建，不需要手点配置：后端的两个（本仓库），以及前端仓库 **mini-vue3-project** 的两个。

| 任务 | 环境 | 做什么 |
| --- | --- | --- |
| `mini-spring-cloud-dev` | dev | 编译 + 单测 + 打镜像；可选重建本地 dev 环境并冒烟 |
| `mini-spring-cloud-prod` | prod | 编译 + 单测 + 打镜像 → **三道闸门** → 晋级 `:prod`（不部署） |
| `mini-vue3-frontend-dev` | dev | 前端：构建镜像（镜像内 `npm ci` + `vite build`）→ 产物自检 → 可选重建前端容器并冒烟 |
| `mini-vue3-frontend-prod` | prod | 前端：构建镜像 → 产物自检 → **三道闸门** → 晋级 `:prod`（不部署） |

前端那两个与后端**同构**（同样的环境推导、同样的 `customWorkspace`、同样的三道闸门），差别只在两处：
前端没有单测所以没有「编译 + 单测」阶段（构建交给它的 Dockerfile 一次做完），
prod 闸门②也从"禁止跳过测试"换成"产物必须环境无关"（挡住把后端地址烤进 bundle）。
前端仓库要挂进来才能读到它的 Jenkinsfile，靠 `docker/infra/.env` 里的 `FRONTEND_DIR`（模板见 `.env.example`）。
前端的部署细节（后端地址放 Nacos、nginx 启动脚本、端口）见前端仓库 README 第七节。

> **部署源提示**：这里的 Jenkins **不拉代码**（`cps` 内联定义 + `customWorkspace` 指向宿主仓库目录，
> 流水线里没有 `checkout`），构建的就是那个工作目录：改完不用 push 就能跑，但**未提交的改动也会进镜像**；
> 分支/提交只用于算 tag 与判 prod 闸门，"改动走独立分支"是为了可追溯。详见 infra README 的「部署源」一节。

环境由**任务名**推导（`JOB_NAME.endsWith('-prod')`），刻意不做成参数：参数能被改写，任务名不能；
命名不符合约定时一律落到 dev，不会因为改名而误发生产。两个任务内联的是**同一份** Jenkinsfile，
所以 dev 与 prod 产出的是同一个制品——prod 只是把它晋级，而不是「为生产再构建一遍」。

| 参数 | 默认 | 作用 |
| --- | --- | --- |
| `IMAGE_TAG` | 空 | 留空 = 用**提交短 SHA**（不可变、可复现、能晋级）；手工调试可填 `local` |
| `RUN_TESTS` | `true` | 关掉等于给 Maven 加 `-DskipTests`；prod 任务禁止关（闸门②） |
| `DEPLOY` | `false` | dev：构建后重建本地环境并冒烟；prod：忽略（本阶段不部署生产） |

阶段：

| 阶段 | 做什么 |
| --- | --- |
| 环境检查 | 校验仓库目录、docker socket、节点上的 docker CLI；打印分支/提交/工作区状态；**prod 在这里过闸门①②** |
| 编译与单测 | 在 `maven:3.9-eclipse-temurin-8` 容器里跑 `mvn -B -ntp clean verify`，测试报告进 Jenkins UI |
| 构建镜像 | 逐模块 `docker build --build-arg MODULE=xxx -f docker/app/Dockerfile`，产出 `<模块>:<tag>` |
| 部署与冒烟 | **仅 dev + `DEPLOY=true`**：`docker compose up -d` → 断言实例读到的是 `[dev]` 配置 → 打通「网关 → 订单 → OpenFeign → 用户」 |
| 生产闸门与晋级 | **仅 prod**：人工审批 → 把同一批镜像打成 `:prod` 指针（不部署） |

### 生产的三道闸门

| 闸门 | 规则 | 为什么 |
| --- | --- | --- |
| ① 来源干净 | 工作区有未提交改动 → 直接失败 | 流水线构建的是工作区而不是某个提交，未提交的改动无法追溯 |
| ② 测试不可跳过 | `RUN_TESTS=false` → 直接失败 | 生产版本不允许来自没跑过测试的构建 |
| ③ 人工审批 | `input` 步骤，审批前不动任何东西 | 唯一需要人点头的事；真实环境应再加 `submitter` 限定审批人 |

晋级的是**同一个制品**：`:prod` 与 `<短SHA>` 指向同一个 image ID（实测比对过）。

命令行触发（无鉴权但开着 CSRF 保护，所以 POST 必须带 crumb）：

```bash
CRUMB=$(curl -s -c /tmp/jk http://127.0.0.1:8080/crumbIssuer/api/json)
H=$(echo "$CRUMB" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['crumbRequestField']+': '+d['crumb'])")

curl -b /tmp/jk -X POST -H "$H" http://127.0.0.1:8080/job/mini-spring-cloud-dev/buildWithParameters
curl -b /tmp/jk -X POST -H "$H" "http://127.0.0.1:8080/job/mini-spring-cloud-dev/buildWithParameters?DEPLOY=true"
curl -b /tmp/jk -X POST -H "$H" http://127.0.0.1:8080/job/mini-spring-cloud-prod/buildWithParameters
```

⚠️ 带参数的任务要用 `buildWithParameters`：用 `/build` 会返回 `400 Nothing is submitted`（实测踩到）。

prod 的审批同样可以用 API（`proceed` 端点要带一个空的 `json` 表单体，否则也是 400）：

```bash
# inputId 从构建页 /job/<job>/<构建号>/input/ 的隐藏字段名里拿
curl -b /tmp/jk -X POST -H "$H" --data-urlencode 'json={"parameter":[]}' \
  "http://127.0.0.1:8080/job/mini-spring-cloud-prod/<构建号>/input/<inputId>/proceed"
```

### 三个刻意为之的设计

| 设计 | 为什么 |
| --- | --- |
| Jenkinsfile 由 JCasC **内联**进 job，而不是用 `cpsScm` | 本环境刻意不引入 Git 服务、代码靠挂载，job 定义里干脆不含 SCM，流水线就不可能去 checkout 把工作区覆盖掉。（`skipDefaultCheckout` 是防御性保留） |
| 流水线的 workspace 就是**挂载进来的仓库目录**（`customWorkspace = $REPO_DIR`） | docker-outside-of-docker 下，构建容器里的路径必须与宿主一致；而且 `docker build .` 的构建上下文得是宿主能访问的真实路径，不能是 Jenkins 卷里的路径 |
| 编译用显式 `docker run`，**不用** `agent { docker { ... } }` | 实测 `agent { docker }` 在「Jenkins 自己跑在容器里 + workspace 是 bind mount」时会失败：插件自动加的 `--volumes-from` 只传播命名卷、不传播 bind mount，于是构建容器里的 durable-task 脚本目录是空的，最终报 `process apparently never started in ...@tmp/durable-xxx`，而且每次都要白等 5 分钟超时。显式 `docker run` 参数全在眼前，还能先在宿主上手工验证 |

代价是 Jenkinsfile 会被内联进 job 定义，所以**改完 Jenkinsfile 要重新应用 JCasC 才生效**：

```bash
# 方式一：重载 JCasC，不重建容器（快）
curl -b /tmp/jk -X POST -H "$H" http://127.0.0.1:8080/configuration-as-code/reload
# 方式二：重建容器（顺手把 .env 里的路径变更也带上）
cd docker/infra && docker compose up -d --force-recreate jenkins
```

Jenkins 镜像本身也做了一处改造：官方 `jenkins/jenkins` 镜像**没有 docker CLI**，而 docker agent 的启动（`docker inspect` / `docker pull`）和镜像构建都要用它，所以 [jenkins/Dockerfile](docker/infra/jenkins/Dockerfile) 从一个 build stage 里把 `docker:cli` 中**静态链接**的 docker / compose / buildx 拷了进来——静态二进制不依赖 alpine 的 musl，拷进 Debian 系镜像即可用，不需要 apt、不需要联网。

⚠️ 流水线构建的是**当前工作区**，不是某个提交：有未提交改动时会一起被构建。环境检查阶段会把这一点明确打印出来（`工作区: 有未提交改动 —— 流水线构建的就是这份工作区，不是某个提交`）。

## 分层与实现要点

- **controller**：只做参数绑定和响应包装，不写业务逻辑；生成的方法把 DTO / QueryVO 直接透传给 service。
- **service**：`XxxService extends IService<Xxx>`，另加 `detail(主键)`、`list(QueryVO)`、`page(PageQueryVO)`，表里有 `deleted` 列时还有 `remove(主键)`；查询一律转调 mapper 的 XML，不拼 `LambdaQueryWrapper`。
- **repository**：`XxxMapper extends BaseMapper<Xxx>`，加 `selectListByCondition`、`selectPageByCondition`（表里有 `deleted` 列时再加 `softDeleteById`）。
- **domain**：`entity` 对应表结构（`@TableName` / `@TableId(AUTO)` / `@TableField` / `@TableLogic`）；`dto` 是接口出入参（不含 `deleted`）；`query` 是查询条件，字段与实体一一对应。
- **common / exception**：`ApiResponse`、`PageResult`、业务异常与全局异常处理。`ApiResponse` / `PageResult` 在 **`mini-common`** 模块里，服务间共用。

其他实现点：

- **查询 SQL 全在 XML**（`mini-user-service/src/main/resources/mapper/*.xml`）：`resultMap` 直接映射 DTO、`Base_Column_List`、`Condition_Where`，两个 `<select>` 复用同一套条件。以后要连表，只改 XML，Java 侧不用动。
- **`Condition_Where` 覆盖 QueryVO 的全部字段**：`String` 走 `like`，其他类型走 `eq`。逻辑删除列特殊：不传时默认 `deleted = 0`，显式传值就按传入值查（因此可以查已删除的数据）。
- **分页**：`PaginationInnerInterceptor`（[MybatisPlusConfig](mini-user-service/src/main/java/com/example/demo/config/MybatisPlusConfig.java)）改写 `LIMIT` 并自动生成 COUNT，`maxLimit = 100`、`overflow = false`；`selectPageByCondition` 的第一个参数是 `IPage`，再由 `PageResult.of(...)` 转成与框架无关的响应对象。
- **删除接口是显式 UPDATE，不是 DELETE**：`@TableLogic` 让 `BaseMapper` 的查询自动追加 `deleted = 0`（XML 里要自己写这个条件）；删除走 `service.remove` → XML 里的 `<update id="softDeleteById">`，行保留在库里，重复删除命中 0 行返回 `false`。**不要用 `updateById` 置这个字段**：MyBatis-Plus 会把逻辑删除列从 SET 子句里剔除，生成 `UPDATE sys_user WHERE id=? AND deleted=0` 这种非法 SQL。
- **Mapper XML 必须在 classpath 里**（`src/main/resources/mapper/`）。XML 在应用启动时加载，改完要重启；若 XML 落到 `src/main/java/...` 下，编译后不在 classpath，运行时会报 `BindingException: Invalid bound statement (not found)`。
- **对象转换统一走 MapStruct**：`UserConverter` 是 `@Mapper(componentModel = "spring")` 接口 + `INSTANCE = Mappers.getMapper(...)`，含 `toDTO / toDTOList / toEntity / toEntityList`，controller 用 `INSTANCE` 静态调用。`maven-compiler-plugin` 的 `annotationProcessorPaths` 按 Lombok → `lombok-mapstruct-binding` → `mapstruct-processor` 顺序声明，否则 MapStruct 看不到 Lombok 生成的访问器。
- **密码**：[PasswordConfig](mini-user-service/src/main/java/com/example/demo/config/PasswordConfig.java) 提供 `BCryptPasswordEncoder` 的 `PasswordEncoder` bean，但当前生成代码没有调用它（保存时按原样入库）。`UserDTO.password` 标了 `@JsonProperty(WRITE_ONLY)`，可写入、不回显。
- **看 SQL**：`logging.level.com.example.demo.repository: debug`（配在 Nacos 的 `mini-user-service.yaml`）会把 mapper 的 SQL 和参数打到控制台。
- **网关模块不要引 `spring-boot-starter-web`**：Gateway 是 WebFlux（Netty）栈，classpath 上同时存在 Spring MVC 会直接启动失败（`Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway`）。同理，`lb://` 依赖 `spring-cloud-starter-loadbalancer`，2021 版没有它会在调用时报找不到负载均衡器。
- **消费端不要复用对方的 DTO**：`mini-order-service` 自己声明 `UserView`（只含用到的字段），不引 `UserDTO`。否则两个服务会在编译期耦合，对方改字段就得一起重新构建。

### 已知缺陷：审计字段不会被填充

表里有 `created_by` / `created_by_name` / `created_at` / `updated_by` / `updated_by_name` / `updated_at`，但生成出来的实体字段上**没有 `@TableField(fill = ...)` 注解**，而 MyBatis-Plus 只在表内存在带 fill 注解的字段时才会回调 `MetaObjectHandler`——所以 [MyBatisMetaObjectHandler](mini-user-service/src/main/java/com/example/demo/config/MyBatisMetaObjectHandler.java) 目前**完全不生效**（`insertFill` / `updateFill` 一次都不会被调用）。实测 SQL 可以看到审计列根本没进语句：

```sql
INSERT INTO sys_user ( username, password, nickname, status ) VALUES ( ?, ?, ?, ? )
UPDATE sys_user SET nickname=?, status=? WHERE id=? AND deleted=0
```

后果：`created_by` / `updated_by` 等恒为 `NULL`；`created_at` / `updated_at` 只在 MySQL 上靠 DDL 的 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` 兜底（H2 测试库没有 `ON UPDATE`，所以更新时不会变），且新增后内存里的实体仍是 `null`，因此 `POST /user/save` 的响应里也看不到审计字段。

要修的话：给 `createdBy` / `createdByName` / `createdAt` 加 `@TableField(fill = FieldFill.INSERT)`，给 `updatedBy` / `updatedByName` / `updatedAt` 加 `@TableField(fill = FieldFill.INSERT_UPDATE)`，并同步代码生成器的实体模板（目前用的是 MyBatis-Plus 内置模板，仓库里没有 `entity.java.vm`）。P3 阶段只把它记录在测试注释与本节中，没有改业务行为。

## 代码生成器

入口 [CodeGenerator.java](mini-user-service/src/main/java/com/example/demo/generator/CodeGenerator.java)，用官方 `com.baomidou:mybatis-plus-generator` 3.5.7（与运行时同版本），由 `exec-maven-plugin` 触发。它没有绑定默认生命周期，所以普通的 `./mvnw clean package` / `./mvnw test` 不会连数据库、也不会生成代码。

固定 7 类之外的文件（DTO / QueryVO / PageQueryVO / Converter）通过 `InjectionConfig.customFile` 输出，模板都在 `mini-user-service/src/main/resources/templates/`。

### 使用方式

```bash
# 默认表（sys_user + auth_tokens），输出到 mini-user-service/src/main/java
./mvnw -pl mini-user-service -Pcodegen process-classes

# 指定表和输出目录；加 -Dgen.fileOverride=true 覆盖已有文件
./mvnw -pl mini-user-service -Pcodegen process-classes \
  -Dgen.tables=sys_user \
  -Dgen.fileOverride=true

# 连 docker/infra 里的 MySQL（默认连的是 localhost:3306，与本项目的 13306 不一致）
./mvnw -pl mini-user-service -Pcodegen process-classes \
  -Ddb.url='jdbc:mysql://127.0.0.1:13306/mini_spring_boot?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'

# 只写到构建目录、不污染源码
./mvnw -pl mini-user-service -Pcodegen process-classes -Dgen.outputDir=target/generated-sources/mybatis-plus
```

IDE 里三种等价方式：

- Maven 面板 → **Profiles** → 勾选 `codegen`，再运行 **Lifecycle → process-classes**；
- 或者先正常构建一次，再运行 **Plugins → exec → exec:java**（`pom.xml` 里已配好 `mainClass`）；
- 或者直接运行 `CodeGenerator` 的 `main()`；要传参就在运行配置的 VM options 里加 `-Dgen.xxx=...`。

生成前表必须已经存在（可以先启动一次应用让 `init.sql` 建表）。

### 可用参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `db.url` | `jdbc:mysql://localhost:3306/mini_spring_boot?...` | JDBC URL |
| `db.username` / `db.password` | `root` / `root` | `-Ddb.password=` 表示空密码 |
| `gen.basePackage` | `com.example.demo` | 根包名 |
| `gen.outputDir` | `src/main/java` | Java 文件输出目录（相对 `mini-user-service`，用 `-pl` 运行时的当前目录） |
| `gen.xmlDir` | `src/main/resources/mapper` | Mapper XML 输出目录，与 `mapper-locations` 对应 |
| `gen.tables` | `sys_user,auth_tokens` | 要生成的表，逗号分隔；库里没有哪张表就用这个参数只指定已有的 |
| `gen.tablePrefix` | `sys_` | 生成实体时去掉的表前缀 |
| `gen.author` | `mini-spring-cloud-project` | 类注释里的作者 |
| `gen.fileOverride` | `false` | 是否覆盖已存在的文件；为 `false` 时已存在的文件会被跳过 |

### 每张表生成的文件

| 文件 | 说明 |
| --- | --- |
| `domain/entity/Xxx.java` | 实体：`@TableName`、`@TableId(AUTO)`、`@TableField`，逻辑删除列带 `@TableLogic` |
| `repository/XxxMapper.java` | `BaseMapper` + `selectListByCondition`、`selectPageByCondition`；有 `deleted` 列时再加 `softDeleteById` |
| `mapper/XxxMapper.xml` | SQL 全在这里：`resultMap` 映射 DTO、`Base_Column_List`、`Condition_Where`；有 `deleted` 列时再加软删除 `<update>` |
| `service/XxxService.java`、`service/impl/XxxServiceImpl.java` | `IService` / `ServiceImpl` + `detail` / `list` / `page`；有 `deleted` 列时再加 `remove` |
| `controller/XxxController.java` | RESTful CRUD，见下表 |
| `domain/dto/XxxDTO.java` | 接口出入参：排除逻辑删除字段；`password` 标 `@JsonProperty(WRITE_ONLY)`；`LocalDateTime` 统一 `yyyy-MM-dd HH:mm:ss` |
| `domain/query/XxxQueryVO.java` | 查询条件：包含实体**全部字段** |
| `domain/query/XxxPageQueryVO.java` | 分页条件：继承 `XxxQueryVO`，增加 `pageNum = 1`、`pageSize = 10` |
| `convert/XxxConverter.java` | MapStruct 转换器：`@Mapper(componentModel = "spring")` 接口 + `INSTANCE` |

生成的 controller 路由见上面「用户服务」一节的表格（生成器按表名的驼峰小写作为前缀，`sys_user` → `/user`）。

查询链路：`Controller`（只透传）→ `Service`（`list` / `page` / `detail`）→ `Mapper` 接口 → **`XxxMapper.xml` 里的 SQL**。分页仍是 MyBatis-Plus 的 `PaginationInnerInterceptor`。

生成器的两处默认调整：MySQL `TINYINT` 在 3.5.7 里默认映射成 `Byte`，这里用 `typeConvertHandler` 改成 `Integer`（与本项目实体、H2 schema 一致）；service 命名用 `formatServiceFileName("%sService")`，避免默认的 `IUserService`。

### 自定义模板

| 模板 | 生成物 |
| --- | --- |
| `controller.java.vm` | `XxxController`（只做参数透传） |
| `service.java.vm` / `serviceImpl.java.vm` | `XxxService` / `XxxServiceImpl` |
| `mapper.java.vm` / `mapper.xml.vm` | `XxxMapper` / `XxxMapper.xml` |
| `dto.java.vm` / `queryVo.java.vm` / `pageQueryVo.java.vm` | `XxxDTO` / `XxxQueryVO` / `XxxPageQueryVO` |
| `converter.java.vm` | `XxxConverter` |

模板里引用共享模型的地方直接写 `com.example.common.web`（`ApiResponse` / `PageResult` 已从本模块的 `common` 包移到 `mini-common`），改 `gen.basePackage` 不影响这一处。改完模板直接重跑生成即可。注意标准模板的路径由引擎自动补 `.vm`，而 `InjectionConfig` 自定义文件的路径是原样使用的，所以 `CodeGenerator` 里统一写成 `/templates/xxx.java.vm`。

### 生成到源码目录时的注意点

默认 `DEFAULT_OUTPUT_DIR = "src/main/java"`，即落在 `mini-user-service/src/main/java/com/example/demo/...`。此时：

- `gen.fileOverride=false`（默认）会跳过已存在的文件，想重新生成某个文件要先把它删掉（或加 `-Dgen.fileOverride=true`）；
- `XxxMapper.xml` 一定要落在 `src/main/resources/mapper/`（`gen.xmlDir` 默认值就是它），否则运行时报 `BindingException`。