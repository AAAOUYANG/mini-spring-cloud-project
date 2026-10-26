# 本地测试环境：基础设施层（MySQL + Nacos + Jenkins + Portainer）

用 Docker 一键拉起微服务所需的四个基础组件（最后一个 Portainer 是**容器可视化**，不是运行依赖）。应用（Spring Cloud Gateway / 业务服务）按**混合模式**跑在宿主机的 IDE 里，迭代最快、可断点调试。

```text
┌────────────────────────── 宿主机（IDE / mvnw / 浏览器）──────────────────────────┐
│                                                                                 │
│   gateway :9000 ──┐                                    Jenkins UI :8080         │
│   user-service    ├── 注册/订阅配置 ──┐                 Nacos 控制台 :8848/nacos  │
│     :8081         │                  │                                          │
└───────────────────┼──────────────────┼──────────────────────────────────────────┘
                    │ localhost:8848   │ localhost:13306
                    │ + 9848(gRPC)     │
┌───────────────────▼──────────────────▼──────────────────────────────────────────┐
│ Docker 网络 mini-net                                                             │
│                                                                                 │
│   ┌─────────────────┐   ┌──────────────────┐   ┌──────────────────────────────┐  │
│   │ mini-mysql      │   │ mini-nacos       │   │ mini-jenkins                 │  │
│   │ mysql:8.0       │◄──│ v2.4.3 arm64     │   │ lts-jdk17 + 88 插件          │  │
│   │ 3306→127.0.0.1  │   │ 配置持久化到 MySQL│   │ JCasC 配置代码化             │  │
│   │    :13306       │   │ 8848/9848/9849   │   │ 挂载 docker.sock 与仓库目录  │  │
│   └─────────────────┘   └──────────────────┘   └──────────────────────────────┘  │
│     mini-mysql-data      mini-nacos-logs         mini-jenkins-home / mini-m2-repo│
└─────────────────────────────────────────────────────────────────────────────────┘
```

> 上图只画了三个组件。compose 里还有第四个 **mini-portainer**（容器可视化，<http://127.0.0.1:9001>）：
> 它挂载 `docker.sock`，把上面这些容器连同本机其它容器一起用网页展示出来（日志、环境变量、端口映射、启停都在页面上点）；
> 数据存在独立卷 `mini-portainer-data` 里，删掉容器再重建不会丢。

## 快速开始

```bash
cd docker/infra

cp .env.example .env      # 首次：务必把 WORKSPACE_DIR 改成仓库根目录的绝对路径（pwd 获取）

docker compose up -d              # 一键启动全部（含一次性任务 nacos-config-init：发布配置中心内容）
docker compose up -d mysql nacos  # 只要 MySQL + Nacos
docker compose up -d portainer    # 只起 Portainer（容器可视化，http://127.0.0.1:9001）
docker compose ps                 # 查看健康状态（nacos-config-init 显示 Exited (0) 属正常）
docker compose logs -f nacos      # 跟踪日志
docker compose run --rm nacos-config-init   # 改完 nacos/config/**（含 env/<环境>/）后重新发布配置
docker compose down               # 停止（保留数据）
docker compose down -v            # 停止并删除数据（下次启动会重新执行 mysql/init/*.sql）
```

**实测结果**：从 `down` 到三个组件全部就绪约 **28 秒**（此前实测）；随后 `nacos-config-init` 在几秒内完成配置发布并退出。

Jenkins 起来后会自带**四个**由 JCasC 创建好的流水线任务：后端的 `mini-spring-cloud-dev` / `mini-spring-cloud-prod`，以及前端仓库 `mini-vue3-project` 的 `mini-vue3-frontend-dev` / `mini-vue3-frontend-prod`。都不需要手动建 job（见「Jenkins 流水线」）。

## 访问入口

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| Nacos 控制台 | http://127.0.0.1:8848/nacos/ | 本地环境已关闭鉴权，直接进入。**记得在「命名空间」下拉里切 `dev`/`prod`**，默认只看得到 `public`（本环境已不使用） |
| Jenkins | http://127.0.0.1:8080 | 无鉴权（仅绑定 127.0.0.1），无需初始密码；内含自动创建的四个流水线任务：后端 `mini-spring-cloud-dev`（<http://127.0.0.1:8080/job/mini-spring-cloud-dev/>）与 `mini-spring-cloud-prod`（<http://127.0.0.1:8080/job/mini-spring-cloud-prod/>），前端 `mini-vue3-frontend-dev`（<http://127.0.0.1:8080/job/mini-vue3-frontend-dev/>）与 `mini-vue3-frontend-prod`（<http://127.0.0.1:8080/job/mini-vue3-frontend-prod/>） |
| MySQL | `127.0.0.1:13306`，root / root | 库：`mini_spring_boot`（dev）、`nacos_config`；`mini_spring_boot_prod` 由 prod 环境的应用按环境层配置自动创建 |
| Portainer | http://127.0.0.1:9001 | 容器可视化。首次打开要创建管理员（密码至少 12 位）；本环境已在 compose 里加 `--no-setup-token`，所以**不需要**去日志里找安装令牌。首次已建好：`admin` / `MiniPortainer2026`（仅本地测试，登录后可自行改密码）。左上角 Environment 选 `local` 即可看到全部容器 |

所有端口都绑定在 `127.0.0.1`，不对外暴露。

## 端口规划

| 服务 | 宿主端口 | 容器端口 | 为什么这样定 |
| --- | --- | --- | --- |
| MySQL | **13306** | 3306 | 宿主 3306 已被本机原生 MySQL 8.4.7 占用，必须错开 |
| Nacos HTTP / 控制台 | 8848 | 8848 | |
| Nacos gRPC | 9848 / 9849 | 9848 / 9849 | **Nacos 2.x 必需**，见下方「坑」第 1 条 |
| Jenkins | 8080 / 50000 | 8080 / 50000 | |
| Portainer | 9001 | 9000 | 容器内固定 9000，而宿主 9000 已被网关（唯一入口）占用，必须错开 |
| 应用（混合模式）Gateway | 9000 | — | 避开 Jenkins 的 8080 |
| 应用（混合模式）业务服务 | 8081 / 8082 | — | 应用跑在宿主 IDE 时用 |
| 应用（容器化）Gateway | 9000 | 9000 | 唯一入口 |
| 应用（容器化）业务服务 | 18081 / 18082 | 8081 / 8082 | 宿主端口故意与混合模式错开：两种模式可共存而不抢端口，排查时也能直连 |

## 目录结构

```text
docker/infra/
├── docker-compose.yml            # 三个组件的编排
├── .env                          # 本机实际配置（已被 .gitignore 忽略）
├── .env.example                  # 配置模板
├── mysql/init/
│   ├── 01-databases.sql          # 建 mini_spring_boot 与 nacos_config
│   └── 02-nacos-schema.sql       # Nacos 建表脚本（提取自镜像，见下）
├── nacos/                        # 配置中心的内容与发布脚本
│   ├── config/*.yaml             # 四个 dataId：mini-common + 每个服务一份
│   └── publish-config.sh         # 幂等发布（可单独跑，也被 nacos-config-init 调用）
└── jenkins/
    ├── Dockerfile                # 预装插件（含硬校验）
    ├── plugins.txt               # 插件清单
    └── casc/jenkins.yaml         # JCasC 配置
```

`02-nacos-schema.sql` 是从镜像里提取的官方脚本，保证与 Nacos 版本精确一致。升级 Nacos 版本时用这条命令重新提取：

```bash
docker run --rm --entrypoint cat nacos/nacos-server:vX.Y.Z /home/nacos/conf/mysql-schema.sql
# 提取后在文件开头补一行 USE nacos_config;（因为 docker-entrypoint-initdb.d 会带着
# MYSQL_DATABASE 执行脚本，不显式 USE 会建到 mini_spring_boot 库里）
```

## 配置中心的内容与发布

配置**按环境分命名空间**（`dev` / `prod`），每个命名空间里是「基础层 + 环境层」两层 dataId。
**改配置改的是文件，不是控制台：**

| 层 | dataId | 源文件 | 内容 |
| --- | --- | --- | --- |
| 基础层 | `mini-common.yaml` | [nacos/config/mini-common.yaml](nacos/config/mini-common.yaml) | Jackson、根日志级别、演示参数 `mini.demo.message` |
| 基础层 | `mini-user-service.yaml` | [nacos/config/mini-user-service.yaml](nacos/config/mini-user-service.yaml) | 数据源、`spring.sql.init`、repository 日志级别 |
| 基础层 | `mini-order-service.yaml` | [nacos/config/mini-order-service.yaml](nacos/config/mini-order-service.yaml) | Feign 超时与日志级别 |
| 基础层 | `mini-gateway.yaml` | [nacos/config/mini-gateway.yaml](nacos/config/mini-gateway.yaml) | Gateway httpclient 超时、网关日志级别 |
| 环境层 | `mini-env.yaml` | [nacos/config/env/dev/mini-env.yaml](nacos/config/env/dev/mini-env.yaml)、[nacos/config/env/prod/mini-env.yaml](nacos/config/env/prod/mini-env.yaml) | **只有环境间不同的值**：数据源、日志级别、环境标识 |

基础层每个环境各发一份（内容相同）；环境层是**独立 dataId**，由各服务在 `spring.config.import`
里最后导入，所以同名键以它为准。刻意不做「同名 dataId 整文件覆盖」：那样环境层写漏一段就会
**静默丢掉**基础层配置，而且控制台里看不出来。

命名空间 id 直接用环境名（`dev` / `prod`）而不用 UUID——控制台里肉眼可读、API 里可手写。脚本会
**幂等创建**命名空间（已存在时 Nacos 返回字面量 `false`，按成功处理）。

```bash
# 自动：docker compose up -d 时由 nacos-config-init 一次性容器发布（默认发 dev prod 两个环境）
# 手动（改了 yaml 之后重新发布，幂等覆盖）：
docker compose run --rm nacos-config-init
docker compose run --rm -e ENVIRONMENTS=dev nacos-config-init        # 只发 dev

# 不想用容器也行，脚本是 POSIX sh，宿主上直接执行：
NACOS_ADDR=127.0.0.1:8848 sh nacos/publish-config.sh
```

脚本会先等 Nacos 就绪，再按「命名空间 → 基础层 → 环境层」逐个发布并**回读校验**。两个细节：
读回请求必须同样带 `tenant`，否则读的是 `public` 命名空间、会把成功误判为失败；回读失败会短暂
重试，因为 Nacos 的读路径有异步缓存（见「常见问题」第 4 条）。

> `public` 命名空间里可能残留早期版本发布的基础配置，现在**不再使用**。判断应用实际读的是哪个
> 命名空间要看 `NACOS_NAMESPACE` 与 `/config/demo` 的返回值，不要看 `public`。

应用侧的接入方式、环境层为什么用独立 dataId、以及怎么验证隔离，见根目录 [README](../../README.md)
的「多环境隔离」与「配置分层」两节。

## Jenkins 流水线

| 项 | 值 |
| --- | --- |
| 任务名 | `mini-spring-cloud-dev` / `mini-spring-cloud-prod`（JCasC 自动创建，不用手点） |
| 环境来源 | **任务名**（`JOB_NAME.endsWith('-prod')`），不是参数——参数能被改写，任务名不能；命名不符合约定时一律落到 dev |
| 定义来源 | 仓库根目录的 [Jenkinsfile](../../Jenkinsfile)，由 JCasC 内联进**两个** job（同一份脚本） |
| 参数 | `IMAGE_TAG`（默认空 = 提交短 SHA）、`RUN_TESTS`（默认 `true`，prod 禁止关）、`DEPLOY`（默认 `false`，仅 dev 生效） |
| 阶段 | 环境检查 → 编译与单测（Java 8 容器）→ 构建镜像 →（dev）部署与冒烟 /（prod）人工审批 + 晋级 `:prod` |

实测耗时：m2 卷与镜像依赖层预热后，dev 任务约 **72 秒**（含部署与冒烟）、纯构建约 **2 分钟**（三个镜像各要重跑一次 Maven 打包）；prod 任务停在审批上的等待时间也会计入构建时长（`timeout` 30 分钟，超时自动中止）。

命令行触发（无鉴权但开了 CSRF，POST 必须带 crumb + 同一 session cookie）：

```bash
CRUMB=$(curl -s -c /tmp/jk http://127.0.0.1:8080/crumbIssuer/api/json)
H=$(echo "$CRUMB" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['crumbRequestField']+': '+d['crumb'])")

# ⚠️ 任务有参数，必须用 buildWithParameters（见「常见问题」第 16 条）
curl -b /tmp/jk -X POST -H "$H" http://127.0.0.1:8080/job/mini-spring-cloud-dev/buildWithParameters
curl -b /tmp/jk -X POST -H "$H" "http://127.0.0.1:8080/job/mini-spring-cloud-dev/buildWithParameters?DEPLOY=true"
curl -b /tmp/jk -X POST -H "$H" http://127.0.0.1:8080/job/mini-spring-cloud-prod/buildWithParameters

# 改了 Jenkinsfile 后让内联内容更新（不重建容器）
curl -b /tmp/jk -X POST -H "$H" http://127.0.0.1:8080/configuration-as-code/reload
```

prod 的审批也可以用 API：
```bash
# inputId = /job/mini-spring-cloud-prod/<构建号>/input/ 页面里那个隐藏字段的 name
curl -b /tmp/jk -X POST -H "$H" --data-urlencode 'json={"parameter":[]}' \
  "http://127.0.0.1:8080/job/mini-spring-cloud-prod/<构建号>/input/<inputId>/proceed"
```

细节与设计取舍（为什么用 `customWorkspace`、为什么不用声明式 docker agent、三道闸门各拦什么）见根 README 的「本地 CI（Jenkins）」一节；踩过的坑见下面「常见问题」第 10–17 条。

### 前端仓库的任务（mini-vue3-frontend-dev / -prod）

Jenkins 里还有前端仓库 **mini-vue3-project** 的两个任务，与后端那两个**同构**：

| 项 | 值 |
| --- | --- |
| 任务名 | `mini-vue3-frontend-dev` / `mini-vue3-frontend-prod` |
| 环境来源 | 同上：任务名（`JOB_NAME.endsWith('-prod')`） |
| 定义来源 | **前端仓库**根目录的 Jenkinsfile，由同一份 `casc/jenkins.yaml` 里第二段 job 脚本内联 |
| 定位方式 | JCasC 注入的全局变量 `FRONTEND_DIR`（值来自 compose 的 `.env`；Jenkins 容器按**完全一致**的路径挂载前端仓库，理由同后端） |
| 参数 | `IMAGE_TAG`（默认空 = 提交短 SHA）、`DEPLOY`（默认 `false`，仅 dev 生效）。前端没有单测，所以**没有** `RUN_TESTS` |
| 阶段 | 环境检查 → 构建镜像（Dockerfile 内完成 `npm ci` + `vite build`）→ 产物自检 →（dev）部署与冒烟 /（prod）人工审批 + 晋级 `:prod` |

两处与后端不同，都是有意的：

- **没有「编译 + 单测」阶段**：前端 `package.json` 里只有 `dev/build/preview`，没有测试与 lint 脚本。
  这里不造一个假的测试阶段，而是把构建交给 Dockerfile 一次做完，流水线只做镜像构建 + 产物自检 + 部署冒烟。
- **prod 闸门②换了内容**：后端是"禁止跳过测试"，前端换成"产物必须环境无关"
  （Dockerfile 固定 `ENV VITE_API_BASE_URL=/api`、产物里能grep 到 `/api` 常量、且不出现绝对 api 基址），
  挡住"把后端地址烤进 bundle"这类最危险的失误 —— 前端的真实后端地址是 nginx 启动时从 Nacos 取的。

要让前端任务可用，`docker/infra/.env` 里必须设置 `FRONTEND_DIR`（前端仓库绝对路径，且必须与宿主路径一致），
Jenkins 容器会把它挂进来；模板见 `.env.example`。前端的部署细节（Nacos 配置项、端口、踩过的坑）见前端仓库 README 第七节。

### 部署源：Jenkins **不拉代码**（刻意如此）

四个任务都是 `cps { script(...) }` 内联定义 + `customWorkspace` 指向宿主仓库目录，流水线里**没有任何 `checkout`**。
所以"部署源"是**宿主机上那个工作目录此刻的样子**：

- 改完**不用 push** 就能跑（本地联调最快），但**未提交的改动也会被打进镜像**；
- 流水线里的分支/提交是只读取出来的，只用于两件事：算镜像 tag（提交短 SHA）、判 prod 闸门①；
- **分支名不影响"构建什么"** —— "改动走独立分支"这条规矩是为了可追溯，以及让 prod 闸门①的"不在主干"有意义。

想换成真实 CI 的「固定分支拉最新」：把 `cps{script}` 换成
`cpsScm{ scm{ git{ remote{url(...)}; branch('*/分支名') } }; scriptPath('Jenkinsfile') }`。前提与代价：

1. 容器要能访问远端（实测 ✅ 能，容器内有 git 2.47.3 + git/git-client 插件；两个仓库都是公开仓库，无需凭据）；
2. **需要先推送** —— 两个仓库远端目前都只有 `main`，本地两条 `docker-deploy` 分支（前端、后端各一条，
   刻意取同一个名字）都还没推，直接切 SCM 会部署回旧版本；
3. 流水线定义会跟着分支走，改 Jenkinsfile **必须 push 才生效**；
4. **后端这条不能直接照搬**：它用 docker agent + `-v` 挂 workspace/m2，要求宿主 docker 能按宿主路径看到
   workspace，而 SCM 的 workspace 在容器内的 `jenkins-home` 卷里（宿主看不见）。前端那条不受影响
   （只用流式上下文的 `docker build` + 不含 bind mount 的 compose）。

## 关键设计决策

| 决策 | 原因 |
| --- | --- |
| Nacos 用 **v2.4.3** 而非 2.3.x | `nacos-server` ≤ v2.3.2 **只有 amd64**，在 Apple Silicon 上要走 QEMU 模拟（启动慢、易假死）；v2.4.0 起才有原生 arm64 |
| Nacos 配置持久化到 MySQL | 默认内嵌 Derby，容器重建后配置易丢；接 MySQL 后与生产形态一致，且能用 SQL 直接查配置 |
| MySQL 映射 **13306** | 宿主 3306 已被本机原生 MySQL 占用 |
| Jenkins 容器用 `user: root` | 挂载 `/var/run/docker.sock` 需要写权限，而宿主 socket 属主是 uid 501，容器内 `jenkins` 用户是 uid 1000（other，无写权限）。**仅限本地，生产禁止** |
| Jenkins 挂载仓库用**完全一致的路径** | docker-outside-of-docker 下，Jenkins 让宿主 docker 启构建容器时会按宿主路径挂载 workspace，容器内外路径不一致就会挂载失败 |
| 插件在**构建期**烘焙进镜像 | 官方镜像入口脚本不读 `plugins.txt`（已实测），不预装就会退化成手点插件的一次性环境 |
| 建 Jenkins 配置全部走 JCasC | 手点的配置删掉容器就没了；`jenkins.yaml` 能进版本库、可重建 |
| 配置中心内容**随仓库管理** | 只写进 Nacos 的 MySQL 是「不可复现」的：`down -v` 一删就没了，新人 clone 下来也不知道有哪些配置项。所以 `nacos/config/*.yaml` 进版本库，并由一次性任务自动发布 |
| 配置发布做成 **compose 一次性任务** | 让「起基础设施」这一步自动带上配置发布，不用额外记一条命令；一次性任务跑完即退出，不常驻占资源 |
| 流水线任务也由 JCasC 定义 | 和插件一样，「手点的 job」删掉容器就没了。job 定义（含内联的流水线脚本）写进 `jenkins.yaml`，容器重建即恢复 |
| Jenkins 镜像里补 docker CLI | 官方 jenkins 镜像**没有** docker CLI，而 docker agent 启动（`docker inspect`/`pull`）与镜像构建都要用它。从一个 build stage 拷贝 `docker:cli` 里的**静态**二进制，不用 apt、不用联网 |

## 本机网络实况（踩过的坑，务必知道）

1. **Docker Hub 不可达**：`registry-1.docker.io` / `hub.docker.com` 均超时，而 Maven Central 正常。
   已在本机 `~/.docker/daemon.json` 配置镜像加速源（该文件在仓库之外）：
   ```json
   "registry-mirrors": [
     "https://docker.m.daocloud.io",
     "https://docker.1ms.run",
     "https://docker.xuanyuan.me"
   ]
   ```
   配置后拉取极快：`nacos/nacos-server:v2.4.3`（295MB）14 秒、`jenkins/jenkins:lts-jdk17`（503MB）11 秒。

2. **Jenkins 插件下载地址时通时不通**：元数据 `updates.jenkins.io/update-center.json` 可达，但插件包会 302 跳到 `get.jenkins.io`，该域名握手经常失败，`jenkins-plugin-cli` 会重试并回落到镜像站，实测仍能装全 88 个。若某次构建因下载失败中断，见 `jenkins/Dockerfile` 里的兜底方案（走 USTC 等镜像站）。

3. **`jenkins-plugin-cli` 会「假成功」**：插件一个都没装上也返回 0。已在 Dockerfile 里加了逐个插件的硬校验。

4. **Docker Desktop 守护进程会卡死**：本次排障时 `docker ps` 挂起 45 秒以上（而 `docker manifest inspect` 因不经过守护进程仍可用）。处理方式：退出并重启 Docker Desktop。

## 常见问题

1. **只映射 8848，服务注册不上、一直重连**
   Nacos 2.x 的客户端走 gRPC，端口是 `8848 + 1000`（9848，客户端请求）和 `+1001`（9849，服务端间同步）。三个端口都要暴露，跑在宿主 IDE 的应用才能注册成功。

2. **改了 `mysql/init/*.sql` 没生效**
   `docker-entrypoint-initdb.d` **只在数据卷为空时执行一次**。必须 `docker compose down -v` 再起。

3. **Nacos 起不来**
   它依赖 MySQL 里的 `nacos_config` 库和表。compose 已用 `depends_on: condition: service_healthy` 保证顺序；若手工改过顺序或删了库表，先按「快速开始」重置。

4. **Nacos 写入配置后立即读不到**
   实测写入后马上 GET 可能返回 `config data not exist`，等约 1 秒再读就正常（异步缓存/提交时序）。写自动化验证脚本时**不要假设强一致**。

5. **Gateway 模块启动直接失败**
   Spring Cloud Gateway 是 WebFlux 技术栈，模块里**不能**有 `spring-boot-starter-web`，否则报
   *Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway*。
   同时需要 `spring-cloud-starter-loadbalancer`，`lb://服务名` 才能解析。

6. **Jenkins 里改了端口，JCasC 里的 URL 忘了改**
   `jenkins/casc/jenkins.yaml` 里 `unclassified.location.url` 是写死的 8080，改 `.env` 的 `JENKINS_HOST_PORT` 时要同步改。

7. **改了 `nacos/config/*.yaml`，服务读到的还是旧值**
   `docker compose up -d` 只在**创建**容器时发布配置，已经跑过的一次性任务不会自动重跑。改完执行 `docker compose run --rm nacos-config-init`。发布后应用侧约 2 秒自动生效（无需重启）；但如果改的是结构性配置（例如网关路由），还需要 `POST /actuator/gateway/refresh`。

8. **`docker exec mini-nacos-config-init ...` 报容器没在运行**
   它是**一次性任务**，跑完即退出，所以 `docker exec` 用不了。要重新发布用 `docker compose run --rm nacos-config-init`（会新建一个容器再跑一遍）。

9. **容器里的应用注册成了 127.0.0.1，网关连不上**
   容器内**不要**设 `NACOS_DISCOVERY_IP=127.0.0.1`：那会把回环地址注册进 Nacos，同网络的网关必然连不上。留空（默认）由 SCA 自动探测容器 IP。宿主上跑混合模式时留空同样正确（注册宿主 LAN IP，同机网关可直连）；只有多网卡导致自动探测选错时，才需要显式指定。

10. **`agent { docker { ... } }` 报 `process apparently never started in ...@tmp/durable-xxx`**
   本环境在这上面折了两次，最终放弃声明式 docker agent：
   - Jenkins 发现自己在容器里，会自动给构建容器加 `--volumes-from <自己>` 来共享 workspace，而它**只传播命名卷、不传播 bind mount**。我们的 workspace 是从宿主 bind 进来的仓库目录，于是构建容器里那份 durable-task 脚本目录是空的。
   - 把 `$REPO_DIR` 与 `$REPO_DIR@tmp` 显式 `-v` 进去也没解决，而且每次都要等满 5 分钟超时才报错。
   流水线因此改成显式 `docker run`（见「Jenkins 流水线」）。若以后非要用 docker agent，先确认 workspace 是**卷**而不是 bind mount。

11. **JCasC 里把 `globalNodeProperties` 写成顶层，Jenkins 直接起不来**
   报 `No configurator found for the following root elements: globalNodeProperties`，随后 Jenkins 初始化失败（`BootFailure`），容器在 `Restarting` 与 `Started` 之间反复循环。正确写法是嵌在 `jenkins:` 下面。
   同一次还发现顶层 `clouds` 也不被接受（`docker-plugin` 明明是 `active=true`，说明是这版 JCasC 没有该根元素），所以本环境不配 clouds。

12. **用 curl 触发构建返回 403**
   无鉴权不等于无保护：Jenkins 默认开着 CSRF 保护，POST 必须带 crumb 且与 session cookie 配套，见「Jenkins 流水线」里的示例。

13. **改了 Jenkinsfile，Jenkins 里跑的却还是旧内容**
   job 里内联的是「应用 JCasC 那一刻读到的」Jenkinsfile。改完要重新应用 JCasC（`/configuration-as-code/reload`，或 `docker compose up -d --force-recreate jenkins`）。可用 `curl http://127.0.0.1:8080/job/mini-spring-cloud-dev/config.xml` 核对内联内容是否已更新（改完 Jenkinsfile 后两个任务的这份 XML 应当与仓库文件逐行一致）。

14. **JCasC 把 job DSL 脚本里的 `${...}` 替换掉了**
   JCasC 会对它读到的整个 yaml 做变量替换（`globalNodeProperties` 里的 `${WORKSPACE_DIR}` 就是这么来的），**`jobs:` 里那段 Groovy 脚本同样在内**。脚本里写 `"mini-spring-cloud-${envName}"` 时 `envName` 不是 JCasC 认识的变量，会被替换成**空串**——任务名成了 `mini-spring-cloud-`（实测踩过，且当时只生成了一个任务，很难第一眼看出）。所以那段脚本里一律用字符串拼接（`'mini-spring-cloud-' + item.name`），不写 Groovy 插值。

15. **改了任务名，旧任务还留在 Jenkins 里**
   JCasC 会创建/更新配置里的任务，但**不会删除**已经从配置中移除的旧任务。把 `mini-spring-cloud` 拆成 dev/prod 两个任务后，旧任务仍在列表里，要手动删：`POST /job/<名字>/doDelete`（带 crumb）。

16. **`POST /job/<名字>/build` 报 `400 Nothing is submitted`**
   任务一旦注册了参数，用 `/build` 触发的请求体是空的，Stapler 直接报这个错。改用 `/buildWithParameters`（不带 query 参数也行，等于全用默认值）。注意流水线**第一次运行之后**参数才会注册到任务上，所以「以前用 `/build` 明明可以」不代表现在还可以。

17. **带 `[...]` 的 tree 查询拿不到结果 / API 审批 prod 报 400**
   - `curl 'http://.../api/json?tree=actions[pendingInputs[id]]'`：裸方括号会被 curl 当成 URL 通配符，加 `-g`（或写成 `%5B`/`%5D`）才有结果，否则 curl 静默失败、你只会看到空输出。挂起的审批项也可以直接从 `/job/<名字>/<构建号>/input/` 页面里找隐藏字段。
   - `POST .../input/<inputId>/proceed` 必须带一个表单体，否则同样是 `400 Nothing is submitted`：`--data-urlencode 'json={"parameter":[]}'`。

18. **JCasC 新建的任务没出现在列表里（但 `jobs/<任务名>/config.xml` 已经写好了）**
   在 `casc/jenkins.yaml` 里新增任务后重启容器：JCasC 会在启动过程中把任务写到磁盘上，
   但**同一次启动**的 job 加载阶段不会再把它读进内存。表现是「日志里明明有
   `createOrUpdateConfig for <任务名>`，Jenkins 里却看不到、直接访问 URL 也是 404」。
   再重启一次 Jenkins（或走一次 JCasC reload）就正常了 —— 这不是配置写错了，别去改 YAML。
   同理：改了 Jenkinsfile 之后也必须重新应用 JCasC，任务里内联的还是旧内容。

19. **Portainer 要求「安装令牌」/ 命令进去没有 shell**
    - 2.4x 起首次初始化需要一个**安装令牌**，默认只打印在容器日志里。compose 已加 `--no-setup-token` 免掉这一步（与 Nacos 不开鉴权、Jenkins 无鉴权保持一致）。若你删掉该参数，就要 `docker logs mini-portainer | grep -A2 -i 'setup token'` 去复制令牌。
    - 这个镜像是**极简镜像，里面没有 shell / curl / wget**（只有 `/portainer` 一个可执行文件），所以：写不了容器内 healthcheck（本服务因此不配 `healthcheck`）、`docker exec -it mini-portainer sh` 也进不去。看状态用 `docker compose ps`，或宿主上 `curl -sI http://127.0.0.1:9001`（返回 `200 OK` 即正常）。
    - 界面**没有中文语言包**：浏览器控制台会报 `locales/zh-CN/translation.json 404`，属正常回退英文，不影响功能。
    - 只有 HTTP 没有 HTTPS：compose 里用 `--http-enabled` 开 HTTP + 只绑 `127.0.0.1`，省掉自签证书告警；要 HTTPS 就删掉该参数并放开注释里的 9443 映射。

20. **改了库以后接口全 500、日志说表不存在（进程却还是 healthy）**
   实测踩过：在控制台把 dev 的 `spring.datasource.url` 换成一个新库后，**进程没重启**数据源就被热切换过去了（日志 `Refresh keys changed: [spring.datasource.url]`，而 `MiniHikariPool` 没有重新启动），但 `sys_user` 是 `spring.sql.init` 在**启动时**用 `init.sql` 建的，新库因此是空的 —— 所有查询都报 `Table '<新库>.sys_user' doesn't exist`，接口 500 且**响应体为空**，健康检查仍是 UP、启动日志也没有异常，很有迷惑性。
   还有一层：全量发布（`docker compose up -d` 会重跑一次性任务 `nacos-config-init`）会把控制台的值覆盖回仓库版本，此时日志里是 `Refresh keys changed: []` —— 配置变了但**不会**自动切回来，连接池仍停在那个空库上。判断"进程实际连的是哪个库"只看 `information_schema.processlist`，别只看 Nacos 控制台，两者可以不一致。
   处置：`docker restart mini-user-service`（或重跑 dev 流水线），然后核对 `docker exec mini-mysql mysql -uroot -proot -N -e "SELECT host, db FROM information_schema.processlist WHERE db IS NOT NULL"` 与 `curl -s 'http://127.0.0.1:18081/user/page?pageNum=1&pageSize=2'`（应为 `code=0` 且有数据）。

## 已实测验证清单

| 验证项 | 结果 |
| --- | --- |
| 一键启动到全部就绪 | ✅ 28 秒（MySQL + Nacos + Jenkins 三组件时实测）；单起 Portainer 约 8 秒 |
| MySQL 两库 + Nacos 12 张表自动建好 | ✅ |
| Nacos 就绪 | ✅ `curl http://127.0.0.1:8848/nacos/v1/console/health/readiness` → `OK` |
| Nacos 控制台可访问 | ✅ http 200 |
| 配置写入 → 落库 MySQL → 读回 → 删除 | ✅ 全链路通 |
| Jenkins 插件预装 | ✅ 88 个，11 个顶层插件全在 |
| JCasC 生效 | ✅ 首页显示 systemMessage |
| 挂载与 socket | ✅ 仓库目录可见、`/var/run/docker.sock` 可写 |
| 配置发布自动完成 | ✅ `nacos-config-init` 退出码 0，4 个 dataId 全部发布且回读成功 |
| 应用从配置中心取到配置 | ✅ `/actuator/env` 属性源含 `DEFAULT_GROUP@mini-common.yaml` 与 `DEFAULT_GROUP@mini-user-service.yaml`，`spring.datasource.url` 来源为 Nacos |
| 配置动态刷新 | ✅ 改 `mini-common.yaml` 后约 2 秒生效，无需重启、无需调用 `/actuator/refresh` |
| 注册 IP 自动探测 | ✅ 宿主上注册 LAN IP，容器内注册容器 IP（172.20.0.x），网关均可路由 |
| 应用镜像构建 | ✅ 三个镜像多阶段构建成功（2m23s；依赖预热层命中后每模块约 7s） |
| 容器化端到端 | ✅ 网关 → 订单服务 → OpenFeign → 用户服务全链路 200；容器内运行用户为非 root 的 `app` |
| 多环境配置隔离 | ✅ 同一 dataId `mini-env.yaml`：dev 命名空间读出 `[dev]`、prod 读出 `[prod]`（`GET /config/demo`） |
| 多环境注册隔离 | ✅ prod 实例注册在 `prod` 命名空间，dev 命名空间里只有 dev 实例；两个环境的网关各自只看得到本环境实例 |
| 多环境数据源隔离 | ✅ prod 实例按环境层配置连上 `mini_spring_boot_prod`（该库由应用自动创建并建表） |
| 多环境靠运行时注入 | ✅ 三个镜像完全不变，只换 `NACOS_NAMESPACE` / `APP_ENV` 即切换环境 |
| 命名空间幂等创建 | ✅ `dev`/`prod` 由脚本创建，已存在时 Nacos 返回 `false` 被按成功处理 |
| 两层配置发布 | ✅ 10 个配置（5 dataId × 2 命名空间）全部发布并回读成功 |
| 流水线环境推导 | ✅ 两个任务内联同一份 Jenkinsfile，日志分别打印 `环境 : dev` / `环境 : prod` |
| 生产闸门①（来源干净） | ✅ prod 在脏工作区下 **1.8 秒**失败，日志给出拒绝原因 |
| 生产闸门②（禁跳测试） | ✅ prod + `RUN_TESTS=false` **4 秒**失败 |
| 生产闸门③（人工审批） | ✅ prod 挂起在 `input`，审批前不动任何东西；API 审批后才继续 |
| 制品晋级（同一镜像） | ✅ `:prod` 与 `:4c17f70` 的 **image ID 完全相同**，不是重新构建 |
| dev 环境断言 | ✅ 部署阶段断言 `/config/demo` 必须含 `[dev]`，接错环境会直接失败 |
| 流水线编译+单测+打镜像 | ✅ dev #1 全绿（124 秒）：Java 8 容器内 `Tests run: 3, Failures: 0, Errors: 0`，三个镜像按提交短 SHA 打标 |
| 流水线部署+冒烟 | ✅ dev #2（`DEPLOY=true`，72 秒）：重建环境 → 环境断言通过 → `/api/order/2?userId=1` 返回 200 且带真实用户数据 |
| Jenkins 任务可复现 | ✅ 容器重建后任务自动重现；`/configuration-as-code/reload` 可让流水线改动生效而无需重建容器 |
| Jenkins 节点可用 docker | ✅ 镜像内 `docker 29.9.2` + `compose 5.6.1` + `buildx`，能连上宿主守护进程 |
| Portainer 容器可视化 | ✅ 免安装令牌建好管理员 → 环境 `local`（`unix:///var/run/docker.sock`）状态 UP → API 与页面都列出本机全部 8 个容器（含健康状态、镜像 tag） |
| 数据源热刷新陷阱 | ✅ 复现并定位：控制台改库 → `Refresh keys changed: [spring.datasource.url]`（连接池未重启）→ 空库无表 → 接口 500 且响应体为空、健康检查仍 UP；`docker restart` 后全部恢复 200 |

复现命令：

```bash
cd docker/infra
docker compose up -d
curl http://127.0.0.1:8848/nacos/v1/console/health/readiness
docker exec mini-mysql mysql -uroot -proot -e "show tables from nacos_config;" | wc -l
curl -s http://127.0.0.1:8080/ | grep -o "mini-spring 本地测试环境.*"
curl -sI http://127.0.0.1:9001 | head -1        # Portainer 应为 HTTP/1.1 200 OK
docker exec mini-jenkins sh -c 'ls /var/jenkins_home/plugins/*.jpi | wc -l'
```

## 下一步

- ~~**P2**：父 pom 聚合，现有工程改造为 `mini-user-service`，新增 `mini-order-service`（OpenFeign）与 `mini-gateway`~~ ✅ 已完成
- ~~**P3**：Nacos 注册发现 + 配置中心（内容随仓库管理、自动发布）、配置环境变量化、全容器化 compose（`docker/app`）、README 更新~~ ✅ 已完成
- ~~**P4**：`Jenkinsfile`（Java 8 工具链的编译 → 单测 → 打镜像）+ 由 JCasC 固化的流水线任务 + 可选部署冒烟~~ ✅ 已完成
- ~~**P5**：多环境隔离（Nacos 命名空间 + 环境层配置 + 容器化环境切换）、Jenkins 拆成 dev/prod 两个任务与生产三道闸门~~ ✅ 已完成
- 后续可选：接真实镜像仓库并用 Jenkins 凭据推送；prod 部署阶段接上目标环境（目前只晋级 `:prod` 指针、不部署）；给 Jenkins 加鉴权（`securityRealm` 与 `authorizationStrategy` 必须成对配置，否则会出现「有鉴权但登不进去」的死局）；给配置中心补一个「改完自动重发」的钩子；prod 库的建表/种子数据由应用侧 DDL 兜着，真实项目应改成带版本的迁移脚本