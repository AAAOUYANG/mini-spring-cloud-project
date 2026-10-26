# Jenkins 镜像：预装插件
#
# 为什么需要自己构建：
#   jenkins/jenkins 官方镜像的入口脚本并不会读取 plugins.txt 自动装插件（已实测确认：
#   /usr/local/bin 下没有任何引用 plugins.txt 的逻辑），所以在构建阶段用
#   jenkins-plugin-cli 把插件烘焙进镜像，做到「删掉容器重建即恢复」，
#   不会退化成手点插件的一次性环境。
#
# 网络说明（实测结论，重要）：
#   - 元数据 https://updates.jenkins.io/update-center.json 可达（3.4MB）；
#   - 插件包下载会 302 跳到 https://get.jenkins.io，该域名在本机网络下时通时不通，
#     jenkins-plugin-cli 会自动重试并回落到镜像站，实测能装全 88 个（含依赖）；
#   - 只有用「官方 URL」这种方式，plugin-cli 才会自动把插件降级到与本镜像核心
#     （2.541.3）兼容的版本。若改成 --jenkins-update-center 指向本地文件，
#     它会直接拿最新版并报 "requires a greater version of Jenkins"，一个都装不上。
#     因此下面的兜底方案必须先写死插件版本。
#
# 重新构建：docker compose build jenkins

# ---------------------------------------------------------------------------
# 构建阶段：从官方 docker:cli 镜像里取 docker CLI（以及 compose / buildx 插件）
#
# 为什么必须装：docker-workflow 启动 docker agent 时，会在**节点上**执行
# `docker inspect` / `docker pull`，节点上没有 docker CLI 就直接
# `docker: not found` + exit 127。另外流水线里的 docker build / docker compose
# 也都在节点上执行。
#
# 可以直接拷的原因：docker:cli 里的 CLI 是**静态链接**的 Go 二进制
# （`ldd` 会报 "Not a valid dynamic program"），不依赖 alpine 的 musl，
# 因此放进 Debian 系的 jenkins 镜像即可用，无需 apt/联网。
# buildx 一并带上，避免 daemon 启用 containerd 镜像存储时 legacy builder 不可用。
# ---------------------------------------------------------------------------
FROM docker:cli AS dockercli

FROM jenkins/jenkins:lts-jdk17

USER root

COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=dockercli /usr/local/libexec/docker/cli-plugins/ /usr/local/libexec/docker/cli-plugins/
RUN chmod 755 /usr/local/bin/docker /usr/local/libexec/docker/cli-plugins/*

# 注意：COPY 会保留宿主文件权限。若宿主文件是 600（rw-------），容器内 jenkins 用户
# 读不到，表现为日志里 "Unable to open /usr/share/jenkins/ref/plugins.txt"，
# 插件一个都装不上，但 jenkins-plugin-cli 仍以 0 退出（假成功）。故显式 chmod。
COPY plugins.txt /tmp/plugins.txt
RUN chmod 644 /tmp/plugins.txt \
 && jenkins-plugin-cli --plugin-file /tmp/plugins.txt \
 && for p in $(grep -vE '^[[:space:]]*(#|$)' /tmp/plugins.txt); do \
        test -f "/usr/share/jenkins/ref/plugins/${p}.jpi" \
          || { echo "❌ 插件安装失败: ${p}"; exit 1; }; \
    done \
 && echo "✅ 插件全部安装成功，共 $(ls /usr/share/jenkins/ref/plugins/*.jpi | wc -l) 个" \
 && chown -R jenkins:jenkins /usr/share/jenkins/ref

# 兜底方案：当官方下载地址完全不通时，把上面两段 RUN 换成下面这段。
# 前提是把 plugins.txt 里的插件写成「名称:版本」，且版本需与本镜像核心兼容，
# 否则会因版本不兼容而全部失败。
#
# RUN curl -fsSL https://updates.jenkins.io/update-center.json -o /tmp/uc.json \
#  && sed -i -e 's#https://updates.jenkins.io/download/plugins#https://mirrors.ustc.edu.cn/jenkins/plugins#g' \
#            -e 's#https://get.jenkins.io/plugins#https://mirrors.ustc.edu.cn/jenkins/plugins#g' \
#            /tmp/uc.json \
#  && jenkins-plugin-cli --plugin-file /tmp/plugins.txt --jenkins-update-center file:///tmp/uc.json
# （可选镜像站：https://mirrors.tuna.tsinghua.edu.cn/jenkins/plugins、
#   https://mirror.nju.edu.cn/jenkins/plugins、https://mirrors.bfsu.edu.cn/jenkins/plugins）

USER jenkins