#!/usr/bin/env groovy
// ============================================================================
// mini-spring-cloud 本地 CI 流水线（dev / prod 共用这一份）
//
// 做三件事：编译 + 单测（Java 8 工具链）→ 用每个模块自己的 Dockerfile 打镜像
//          →（dev）用刚打出来的镜像重建本地环境并冒烟
//          →（prod）人工审批后把同一批镜像晋级为 :prod 发布指针
//
// 环境怎么区分（详见根目录 README「多环境隔离」）：
//   任务名就是环境：mini-spring-cloud-dev / mini-spring-cloud-prod，两个任务内联同一份
//   本文件。环境从 JOB_NAME 推导，**刻意不做成参数**——参数能被改写，任务名不能。
// 环境差异本身不在这里：镜像只有一个，环境差异全部由 Nacos 命名空间 + env 文件注入，
//   dev/prod 构建的产物是同一个东西（这才叫「晋级」而不是「各构建一遍」）。
//
// 三道生产闸门（都在本文件里硬编码，不靠人自觉）：
//   ① 工作区必须干净（有未提交改动一律拒绝——生产不允许来源不明）
//   ② 不允许跳过测试
//   ③ 人工审批（input）之后才打 :prod 指针
//
// 三个关键的、容易踩错的设计点：
//
// 1. 构建的就是「挂载进 Jenkins 的宿主仓库目录」，不是 Jenkins 的 workspace 副本。
//    顶层 agent 用 customWorkspace 把 workspace 直接指向 $REPO_DIR，这样
//    junit / archiveArtifacts / docker build . 这些按「相对 workspace」解析的步骤
//    天然落在真实仓库上。
//    更关键的是 docker-outside-of-docker：Jenkins 是让**宿主 docker** 去启构建容器，
//    构建容器里的路径必须与宿主一致，否则挂载/构建上下文都会失败。这里 $REPO_DIR
//    在宿主和容器里是同一个路径（docker/infra compose 刻意这样挂的），所以成立。
//
// 2. 编译用 maven:3.9-eclipse-temurin-8 镜像，不用 Jenkins 节点的 JDK。
//    Jenkins 跑在 JDK 17 上，而本工程编译目标是 1.8；单测行为也必须与 Java 8 一致
//    （H2、MyBatis 反射等都可能随 JDK 版本变化），所以构建环境必须固定成 Java 8。
//    镜像里自带 mvn，因此不调 ./mvnw（那会再下载一份 Maven）。
//
// 3. 镜像构建与部署阶段直接在 Jenkins 节点上执行 docker 命令。
//    Jenkins 官方镜像里**没有 docker CLI**，所以 docker/infra/jenkins/Dockerfile
//    从 docker:cli 里把静态链接的 docker / compose / buildx 拷了进来。
//
// 代码来源没有 Git 服务：仓库以完全一致的路径挂在 Jenkins 里，job 定义里也不含 SCM，
// 所以流水线绝不会去 checkout 而覆盖工作区（options.skipDefaultCheckout 是防御性保留）。
// ============================================================================

pipeline {
    agent {
        node {
            // label 是 node agent 的必填项（不写会在解析阶段直接报
            // "Missing required parameter for agent type node: label"）；
            // 本环境只有内置节点，标签就是 built-in。
            label 'built-in'
            // REPO_DIR 由 JCasC 的 jenkins.globalNodeProperties 注入；
            // WORKSPACE_DIR 是 compose 传进容器的进程环境变量，作为兜底。
            customWorkspace "${env.REPO_DIR ?: env.WORKSPACE_DIR}"
        }
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
        // 防御性：本 job 不含 SCM，正常不会有隐式 checkout；万一以后改成 SCM 定义，
        // 这行能防止 checkout 覆盖掉 $REPO_DIR 里未提交的改动。
        skipDefaultCheckout(true)
    }

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: '',
               description: '镜像 tag。留空 = 用提交短 SHA（不可变、可晋级）；手工调试 dev 时可填 local')
        booleanParam(name: 'RUN_TESTS', defaultValue: true,
               description: '是否执行单元测试。prod 任务强制为 true（闸门②）')
        booleanParam(name: 'DEPLOY', defaultValue: false,
               description: 'dev：构建后重建本地环境并冒烟。prod：忽略（本阶段不部署生产）')
    }

    environment {
        MAVEN_IMAGE = 'maven:3.9-eclipse-temurin-8'
        M2_VOLUME   = 'mini-m2-repo'
        DOCKER_SOCK = '/var/run/docker.sock'
        APP_MODULES = 'mini-user-service mini-order-service mini-gateway'

        // 环境从**任务名**推导：mini-spring-cloud-dev -> dev，mini-spring-cloud-prod -> prod。
        // 本环境没装 env-inject 插件，无法给不同任务注入不同的环境变量，任务名是最可靠的
        // 单一来源。命名不符合约定时一律落到 dev —— 不会因为改名而误发生产。
        APP_ENV = "${env.JOB_NAME.endsWith('-prod') ? 'prod' : 'dev'}"
    }

    stages {

        stage('环境检查') {
            steps {
                script {
                    sh '''
                        set -eu
                        echo "环境          : $APP_ENV"
                        echo "仓库目录      : $REPO_DIR"
                        [ -n "${REPO_DIR:-}" ] || { echo "❌ REPO_DIR 为空：应由 JCasC 全局环境变量注入"; exit 1; }
                        [ -f "$REPO_DIR/pom.xml" ] || { echo "❌ $REPO_DIR 下找不到 pom.xml"; exit 1; }
                        [ -f "$REPO_DIR/docker/app/Dockerfile" ] || { echo "❌ 找不到 docker/app/Dockerfile"; exit 1; }
                        [ -f "$REPO_DIR/docker/app/env/$APP_ENV.env" ] || { echo "❌ 找不到 docker/app/env/$APP_ENV.env"; exit 1; }
                        [ -S "$DOCKER_SOCK" ] || { echo "❌ 找不到 docker socket $DOCKER_SOCK"; exit 1; }

                        echo "当前分支      : $(git -C "$REPO_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo 未知)"
                        echo "当前提交      : $(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo 未知)"
                        DIRTY=0
                        if [ -n "$(git -C "$REPO_DIR" status --porcelain 2>/dev/null)" ]; then DIRTY=1; fi

                        echo "Jenkins 节点  : $(java -version 2>&1 | head -1)（仅用于调度，编译用的是 Java 8 镜像）"
                        echo "Maven 镜像    : $MAVEN_IMAGE"
                        command -v docker >/dev/null 2>&1 \
                            || { echo "❌ 节点上没有 docker CLI（见 docker/infra/jenkins/Dockerfile）"; exit 1; }
                        echo "Docker 客户端 : $(docker --version)"
                        echo "Docker 服务端 : $(docker info --format '{{.ServerVersion}}' 2>/dev/null || echo '❌ 连不上 docker.sock')"
                        echo "Compose       : $(docker compose version --short 2>/dev/null || echo 无)"

                        echo ""
                        if [ "$APP_ENV" = "prod" ]; then
                            # ---- 生产闸门 ①：来源必须干净 ----
                            if [ "$DIRTY" -eq 1 ]; then
                                echo "❌ 生产闸门①：工作区有未提交改动，拒绝构建生产版本"
                                echo "   流水线构建的是工作区而不是某个提交，未提交的改动无法追溯。"
                                echo "   请先提交（或先用 dev 任务验证），再重新触发本任务。"
                                git -C "$REPO_DIR" status --short | head -10
                                exit 1
                            fi
                            echo "✅ 生产闸门①：工作区干净"
                            # ---- 生产闸门 ②：测试不可跳过 ----
                            if [ "${RUN_TESTS:-true}" != "true" ]; then
                                echo "❌ 生产闸门②：生产构建禁止跳过测试（RUN_TESTS=false）"
                                exit 1
                            fi
                            echo "✅ 生产闸门②：测试不可跳过"
                            echo "⏳ 生产闸门③：人工审批（在「生产闸门与晋级」阶段，审批前不会动任何东西）"
                            echo "   注意：本阶段不部署生产，只产出可发布的镜像指针。"
                        else
                            if [ "$DIRTY" -eq 1 ]; then
                                echo "工作区        : 有未提交改动 —— 流水线构建的就是这份工作区，不是某个提交"
                            else
                                echo "工作区        : 干净"
                            fi
                            echo "环境性质      : dev（允许未提交改动；干净工作区/禁跳测试/审批只对 prod 任务生效）"
                        fi
                    '''

                    // 镜像 tag：默认用提交短 SHA（不可变，便于「同一制品晋级」），可用参数覆盖
                    def shortSha = sh(script: "git -C '${env.REPO_DIR}' rev-parse --short HEAD",
                                      returnStdout: true).trim()
                    def tag = params.IMAGE_TAG?.trim() ? params.IMAGE_TAG.trim() : shortSha
                    env.RESOLVED_IMAGE_TAG = tag
                    echo "镜像 tag      : ${tag}${params.IMAGE_TAG?.trim() ? '' : '（默认取自提交短 SHA）'}"
                }
            }
        }

        stage('编译与单测') {
            // 刻意不用 Jenkins 的 agent { docker { ... } }：
            // 本环境实测它在「docker-outside-of-docker + bind mount 的 workspace」下会失败。
            // Jenkins 检测到自己跑在容器里时，会自动给构建容器加 --volumes-from <自己>
            // 来共享 workspace，但 --volumes-from **只传播命名卷，不传播 bind mount**；
            // 我们的 workspace 是从宿主 bind 进来的仓库目录，于是构建容器里的
            // durable-task 脚本目录是空的，最终报
            //   "process apparently never started in ...@tmp/durable-xxx"
            // 而且每次都要等满 5 分钟超时才失败。
            // 显式 docker run 没有这层插件魔法，参数全在眼前，也便于在宿主上先手工验证；
            // 它和 docker/app/Dockerfile 里的构建方式（镜像内跑 Maven）也保持一致。
            steps {
                script {
                    def mvnCmd = "docker run --rm" +
                        " -v '${env.REPO_DIR}':'${env.REPO_DIR}'" +
                        " -v ${env.M2_VOLUME}:/root/.m2" +
                        " -e TZ=Asia/Shanghai" +
                        " -w '${env.REPO_DIR}'" +
                        " ${env.MAVEN_IMAGE}" +
                        " mvn -B -ntp clean verify ${params.RUN_TESTS ? '' : '-DskipTests'}"
                    echo "执行：${mvnCmd}"
                    sh mvnCmd
                }
            }
            post {
                always {
                    // 测试报告进 Jenkins UI（RUN_TESTS=false 时没有 XML，allowEmptyResults 兜底）
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('构建镜像') {
            // 节点上已有 docker CLI，直接执行即可，不需要 docker:cli 作 agent
            steps {
                script {
                    for (module in env.APP_MODULES.split(' ')) {
                        echo "构建镜像 ${module}:${env.RESOLVED_IMAGE_TAG}"
                        sh "docker build --build-arg MODULE=${module} -f docker/app/Dockerfile -t ${module}:${env.RESOLVED_IMAGE_TAG} ."
                    }
                    sh "docker image ls --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep -E '^(mini-user-service|mini-order-service|mini-gateway):' | sort"
                }
            }
        }

        // dev 本地部署 + 冒烟。prod 不走这里（本阶段不做生产部署）。
        stage('部署与冒烟') {
            when {
                expression { return env.APP_ENV == 'dev' && params.DEPLOY }
            }
            steps {
                sh '''
                    set -eu
                    echo "重建 $APP_ENV 环境（IMAGE_TAG=$RESOLVED_IMAGE_TAG，环境文件 docker/app/env/$APP_ENV.env）"
                    APP_ENV="$APP_ENV" IMAGE_TAG="$RESOLVED_IMAGE_TAG" \
                        docker compose -f docker/app/docker-compose.yml up -d

                    echo "等待网关就绪（最多 120 秒）..."
                    ready=0
                    for i in $(seq 1 40); do
                        if docker exec mini-gateway curl -fsS http://127.0.0.1:9000/actuator/health >/dev/null 2>&1; then
                            ready=1; break
                        fi
                        sleep 3
                    done
                    if [ "$ready" -ne 1 ]; then
                        echo "❌ 网关 120 秒内未就绪"
                        docker compose -f docker/app/docker-compose.yml logs --tail 40
                        exit 1
                    fi

                    # 环境校验：实例读到的必须是本任务对应的环境配置。
                    # 同一镜像在 dev/prod 下读不同命名空间的同名 dataId，这一步能直接发现接错环境。
                    demo=$(docker exec mini-user-service curl -fsS http://127.0.0.1:8081/config/demo || true)
                    case "$demo" in
                        *"[$APP_ENV]"*) echo "✅ 环境校验：实例读到的是 $APP_ENV 的配置（$demo）" ;;
                        *) echo "❌ 环境校验失败：期望含 [$APP_ENV]，实际 $demo"; exit 1 ;;
                    esac

                    echo "冒烟：网关 → 订单服务 → OpenFeign → 用户服务"
                    code=$(docker exec mini-gateway curl -s -o /tmp/smoke.json -w '%{http_code}' \
                        "http://127.0.0.1:9000/api/order/$BUILD_NUMBER?userId=1")
                    body=$(docker exec mini-gateway cat /tmp/smoke.json 2>/dev/null || true)
                    echo "响应码 $code：$body"
                    [ "$code" = "200" ] || { echo "❌ 冒烟失败"; exit 1; }
                    echo "✅ 冒烟通过（HTTP 200）"
                '''
            }
        }

        // prod 的闸门③与晋级。审批通过前不动任何东西；本阶段不做生产部署。
        stage('生产闸门与晋级') {
            when {
                expression { return env.APP_ENV == 'prod' }
            }
            steps {
                // 本环境无鉴权，submitter 留空 = 任何能触发任务的人都可以点。
                // 真实环境应写 submitter: 'release-managers' 限定审批人。
                input message: "prod：即将把镜像 ${env.RESOLVED_IMAGE_TAG} 晋级为 :prod。确认继续？", ok: '确认晋级'
                script {
                    for (module in env.APP_MODULES.split(' ')) {
                        sh "docker tag ${module}:${env.RESOLVED_IMAGE_TAG} ${module}:prod"
                    }
                    sh "docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E ':prod' | sort"
                    echo "已晋级：${env.APP_MODULES} → :prod（指向 ${env.RESOLVED_IMAGE_TAG}）"
                    echo "本阶段不做生产部署（未接目标环境）。真要发布时："
                    echo "  APP_ENV=prod IMAGE_TAG=prod docker compose -f docker/app/docker-compose.yml up -d"
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'mini-*/target/*.jar', allowEmptyArchive: true
        }
        success {
            script {
                if (env.APP_ENV == 'prod') {
                    echo "✅ 流水线成功：生产版本 ${env.RESOLVED_IMAGE_TAG} 已晋级为 :prod（未部署）"
                } else {
                    echo "✅ 流水线成功：${env.APP_MODULES} → 镜像 tag ${env.RESOLVED_IMAGE_TAG}"
                }
            }
        }
        failure {
            echo "❌ 流水线失败，控制台日志见上方"
        }
    }
}