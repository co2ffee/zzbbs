#!/bin/bash
# 自动部署两个前端静态文件：定时检查 GitHub 上 deploy-latest 发布包有没有更新，有就跑现有的部署脚本。
# 只处理前端（用户端 /、管理端 /admin-ui/）；后端要重启，不在这里，仍然手动 deploy.sh。
#
# 放在服务器 /home/pybbs/auto-deploy-ui.sh，由 systemd timer 每 10 分钟运行一次（见 deploy/INSTALL_AUTODEPLOY.md）。
# 依赖：curl、python3、flock、bash；以及 /home/pybbs/deploy-forum-ui.sh、deploy-admin-ui.sh（已有）。
#
# 流程：GitHub API 查 release 里资源的 id + updated_at（重新上传会变）→ 和上次部署成功时记下的对比 →
#       不同就执行对应的 deploy-*.sh → 健康检查（nginx 返回 200）→ 失败就自动回滚到上一版并记下“坏版本”，不再重复尝试同一个版本。
set -u

APP_DIR=${APP_DIR:-/home/pybbs}
API_BASE=${API_BASE:-https://api.github.com}
# 访问 GitHub 走本机 mihomo 代理；测试时可以置空
CURL_OPTS=${CURL_OPTS---socks5 127.0.0.1:7891}
# 健康检查：直接请求本机 nginx（跳过证书校验，用 Host 头指定站点）
HEALTH_BASE=${HEALTH_BASE:-https://127.0.0.1}
HEALTH_HOST=${HEALTH_HOST:-dljdlj.top}

# 可选：GitHub 个人访问令牌（不需要任何权限，公开仓库只用来提高 API 限流额度 60 → 5000 次/小时）。
# 代理出口 IP 被别人用满限流时才需要；写在 /etc/zzbbs-autodeploy.env（GITHUB_TOKEN=xxx），不要写进脚本。
AUTH_HEADER=()
[ -n "${GITHUB_TOKEN:-}" ] && AUTH_HEADER=(-H "Authorization: Bearer $GITHUB_TOKEN")

STATE_DIR="$APP_DIR/.autodeploy"
LOG="$APP_DIR/autodeploy.log"
mkdir -p "$STATE_DIR"

log() { echo "$(date '+%F %T') $*" | tee -a "$LOG"; }

# 同一时间只跑一个（上一次还没跑完就跳过）
exec 9>"$STATE_DIR/lock"
flock -n 9 || exit 0

# 日志超过 1MB 只留最后 2000 行
if [ -f "$LOG" ] && [ "$(stat -c %s "$LOG" 2>/dev/null || echo 0)" -gt 1048576 ]; then
    tail -n 2000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
fi

command -v python3 >/dev/null || { log "ERROR 没有 python3，无法解析 GitHub API 返回，退出"; exit 1; }

# check_one 名字 仓库 资源文件名 部署脚本 目录 健康检查路径
check_one() {
    local name="$1" repo="$2" asset="$3" script="$4" dir="$5" path="$6"
    local json sig state bad code

    # shellcheck disable=SC2086
    if ! json=$(curl $CURL_OPTS -fsS --max-time 30 -H 'Accept: application/vnd.github+json' ${AUTH_HEADER[@]+"${AUTH_HEADER[@]}"} \
            "$API_BASE/repos/$repo/releases/tags/deploy-latest" 2>&1); then
        log "WARN [$name] 查询 GitHub release 失败（网络/限流），下次再试: $(echo "$json" | head -c 200)"
        return 0
    fi
    sig=$(printf '%s' "$json" | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
    for a in d.get("assets", []):
        if a.get("name") == sys.argv[1]:
            print("%s:%s" % (a.get("id"), a.get("updated_at")))
            break
except Exception:
    pass
' "$asset")
    if [ -z "$sig" ]; then
        log "WARN [$name] release 里没有找到 $asset，跳过"
        return 0
    fi

    state=$(cat "$STATE_DIR/$name.state" 2>/dev/null || true)
    bad=$(cat "$STATE_DIR/$name.bad" 2>/dev/null || true)
    [ "$sig" = "$state" ] && return 0     # 已经是最新
    [ "$sig" = "$bad" ] && return 0       # 这个版本之前部署失败过，等下一个新版本

    log "INFO [$name] 发现新版本 $sig（当前 ${state:-无记录}），开始部署"
    if ! bash "$APP_DIR/$script" >>"$LOG" 2>&1; then
        log "ERROR [$name] $script 执行失败，保留当前线上版本，下次再试（详情见上面的输出）"
        return 0
    fi

    # 健康检查：nginx 能返回 200
    code=$(curl -sk --max-time 10 -o /dev/null -w '%{http_code}' -H "Host: $HEALTH_HOST" "$HEALTH_BASE$path" 2>/dev/null || echo 000)
    if [ "$code" != "200" ]; then
        log "ERROR [$name] 部署后健康检查失败（$HEALTH_BASE$path 返回 $code），自动回滚到上一版"
        if [ -d "$APP_DIR/$dir.bak" ]; then
            rm -rf "$APP_DIR/$dir.failed"
            mv "$APP_DIR/$dir" "$APP_DIR/$dir.failed"
            mv "$APP_DIR/$dir.bak" "$APP_DIR/$dir"
            log "ERROR [$name] 已回滚；坏版本的文件留在 $dir.failed 供排查，该版本不会再自动重试"
        else
            log "ERROR [$name] 没有上一版备份可回滚，请人工处理"
        fi
        echo "$sig" > "$STATE_DIR/$name.bad"
        return 0
    fi

    echo "$sig" > "$STATE_DIR/$name.state"
    rm -f "$STATE_DIR/$name.bad"
    log "INFO [$name] 部署成功 $sig"
}

check_one forum-ui co2ffee/zzbbs-forum-web forum-ui.tar.gz deploy-forum-ui.sh forum-ui "/"
check_one admin-ui co2ffee/zzbbs-admin-web admin-ui.tar.gz deploy-admin-ui.sh admin-ui "/admin-ui/"
exit 0
