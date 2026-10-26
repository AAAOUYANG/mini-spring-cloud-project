#!/bin/sh
# ============================================================================
# 把 nacos/config 下的配置发布到 Nacos 配置中心（幂等：同名 dataId 直接覆盖）
#
# 环境隔离：一个环境一个 Nacos **命名空间**，命名空间 id 与环境名同名（dev / prod）。
# 应用通过 NACOS_NAMESPACE 选择命名空间，同一个 dataId 在不同环境可以是不同内容。
#
# 目录约定（两层，各自是独立 dataId，运行时由 Spring 按导入顺序合并）：
#   config/*.yaml              「基础层」：所有环境共有的配置，每个命名空间都会发一份
#   config/env/<env>/*.yaml    「环境层」：只放该环境的差异，文件名即 dataId（约定 mini-env.yaml）
#   合并规则：application.yml 的 spring.config.import 把环境层放在最后，同名键以环境层为准。
#   为什么不直接「整文件覆盖同名 dataId」？因为那样一旦覆盖层写漏一段，基础层配置会被
#   **静默丢掉**（配置中心里看不出少了什么）。独立 dataId 只描述差异，没有这个风险。
#
# 用法：
#   ./publish-config.sh                          # 发布 ENVIRONMENTS（默认 dev prod）
#   ENVIRONMENTS=dev ./publish-config.sh         # 只发 dev
#   NACOS_ADDR=nacos:8848 ./publish-config.sh    # 容器内用服务名
#
# 单独跑（不带参数）时的前置条件：docker compose up -d nacos
# 也可由 compose 里的 nacos-config-init 一次性容器自动调用。
#
# 这个脚本存在的意义：配置必须能随代码仓库复现。
# 只把配置写进 Nacos 的库里是不够的——数据卷一删（docker compose down -v）
# 配置就没了，新人 clone 下来也无从得知有哪些配置项。
# ============================================================================
set -eu

NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
# 需要发布的环境列表；命名空间 id = 环境名
ENVIRONMENTS="${ENVIRONMENTS:-dev prod}"
CONFIG_DIR="${CONFIG_DIR:-$(dirname "$0")/config}"
BASE_URL="http://${NACOS_ADDR}/nacos/v1/cs/configs"
NS_URL="http://${NACOS_ADDR}/nacos/v1/console/namespaces"

if [ ! -d "$CONFIG_DIR" ]; then
    echo "❌ 配置目录不存在: $CONFIG_DIR" >&2
    exit 1
fi

# ---------------------------------------------------------------- 等 Nacos 就绪
i=0
while [ "$i" -lt 60 ]; do
    if curl -fsS -m 3 "http://${NACOS_ADDR}/nacos/v1/console/health/readiness" >/dev/null 2>&1; then
        break
    fi
    i=$((i + 1))
    sleep 2
done
if [ "$i" -ge 60 ]; then
    echo "❌ Nacos(${NACOS_ADDR}) 在 120 秒内未就绪" >&2
    exit 1
fi

# ---------------------------------------------------------------- 命名空间
# 幂等：不存在则建；已存在时 Nacos 返回字面量 false，同样算成功。
ensure_namespace() {
    ns="$1"
    desc="$2"
    resp=$(curl -fsS -m 5 -X POST "$NS_URL" \
        --data-urlencode "customNamespaceId=${ns}" \
        --data-urlencode "namespaceName=${ns}" \
        --data-urlencode "namespaceDesc=${desc}" 2>/dev/null || true)
    case "$resp" in
        true)  echo "  ➕ 新建命名空间 ${ns}" ;;
        false) echo "  ✅ 命名空间 ${ns} 已存在" ;;
        *)     echo "❌ 创建命名空间 ${ns} 失败（返回: ${resp:-空}）" >&2; exit 1 ;;
    esac
}

# ---------------------------------------------------------------- 发布单个文件
# publish_file <文件> <命名空间>
publish_file() {
    file="$1"
    tenant="$2"
    data_id=$(basename "$file")

    # --data-urlencode name@file：读文件内容并做 URL 编码，避免内容里的 & # 等字符破坏请求
    if ! curl -fsS -X POST "$BASE_URL" \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "group=${NACOS_GROUP}" \
        --data-urlencode "tenant=${tenant}" \
        --data-urlencode "type=yaml" \
        --data-urlencode "content@${file}" >/dev/null; then
        echo "  ❌ ${data_id} 发布失败" >&2
        exit 1
    fi

    # 回读校验：Nacos 的读路径有异步缓存，刚写完可能读不到，短暂重试
    ok=0
    j=0
    while [ "$j" -lt 10 ]; do
        got=$(curl -fsS -m 3 -G "$BASE_URL" \
            --data-urlencode "dataId=${data_id}" \
            --data-urlencode "group=${NACOS_GROUP}" \
            --data-urlencode "tenant=${tenant}" 2>/dev/null || true)
        if [ -n "$got" ] && [ "$got" != "config data not exist" ]; then
            ok=1
            break
        fi
        j=$((j + 1))
        sleep 1
    done

    bytes=$(wc -c <"$file" | tr -d ' ')
    if [ "$ok" -eq 1 ]; then
        echo "  ✅ ${data_id} (${bytes} bytes)"
    else
        echo "  ⚠️  ${data_id} 已提交但回读为空（Nacos 读路径异步，稍后重试即可）" >&2
    fi
}

# ---------------------------------------------------------------- 按环境发布
total=0
for env in $ENVIRONMENTS; do
    echo ""
    echo "=== 环境 ${env}（Nacos 命名空间 tenant=${env}）==="
    ensure_namespace "$env" "mini-spring-cloud 本地环境：${env}"

    echo "  基础层 config/*.yaml（所有环境共有）："
    for file in "$CONFIG_DIR"/*.yaml; do
        [ -e "$file" ] || continue
        publish_file "$file" "$env"
        total=$((total + 1))
    done

    if [ -d "$CONFIG_DIR/env/$env" ]; then
        echo "  环境层 config/env/${env}/*.yaml（独立 dataId，优先级高于基础层）："
        for file in "$CONFIG_DIR/env/$env"/*.yaml; do
            [ -e "$file" ] || continue
            publish_file "$file" "$env"
            total=$((total + 1))
        done
    fi
done

# ---------------------------------------------------------------- 结果
echo ""
echo "完成：已发布 ${total} 个配置到 ${NACOS_ADDR}（group=${NACOS_GROUP}，环境: ${ENVIRONMENTS}）"
echo ""
echo "各环境的差异一览（环境层内容 + 该环境实际生效的数据源）："
for env in $ENVIRONMENTS; do
    envcfg=$(curl -fsS -m 5 -G "$BASE_URL" \
        --data-urlencode "dataId=mini-env.yaml" \
        --data-urlencode "group=${NACOS_GROUP}" \
        --data-urlencode "tenant=${env}" 2>/dev/null || true)
    demo=$(echo "$envcfg" | grep -oE '\[[a-z]+\]' | head -1 || true)
    # 数据源只从 JDBC URL 里取库名：不能直接 grep 库名——注释里出现同名字样会先命中
    db=$(echo "$envcfg" | sed -n 's#.*jdbc:mysql://[^/]*/\(mini_spring_boot[a-z_]*\).*#\1#p' | head -1)
    # 环境层没写数据源，说明沿用基础层（dev 就是这种情况）
    if [ -z "$db" ]; then
        db=$(curl -fsS -m 5 -G "$BASE_URL" \
            --data-urlencode "dataId=mini-user-service.yaml" \
            --data-urlencode "group=${NACOS_GROUP}" \
            --data-urlencode "tenant=${env}" 2>/dev/null |
            sed -n 's#.*jdbc:mysql://[^/]*/\(mini_spring_boot[a-z_]*\).*#\1#p' | head -1)
    fi
    echo "  ${env}: 环境标识=${demo:-未知}  数据源=${db:-未知}"
done