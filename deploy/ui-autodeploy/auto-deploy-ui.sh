#!/bin/bash
# 前端自动部署：发现 GitHub deploy-latest 里的前端包有新版本就调用现有 deploy-*-ui.sh 上线。
# 只管前端静态文件（用户端 forum-ui、管理端 admin-ui），不碰后端。
# 由 systemd timer 每 10 分钟调用一次，也可手动运行：bash /home/pybbs/auto-deploy-ui.sh
#
# 版本判断：对下载地址做 HEAD，取最终响应的 etag+content-length+last-modified。
# 不用 GitHub API：服务器走 mihomo 代理出口，未登录 API 额度(60次/小时)是和别人共用的，已经被用光(403)。
set -u
BASE=/home/pybbs
STATE=$BASE/autodeploy
LOG=$BASE/autodeploy.log
PROXY=127.0.0.1:7891
HOST=dljdlj.top
mkdir -p "$STATE"

log() { echo "[$(date '+%F %T')] $*" >> "$LOG"; }

# 日志超过 1MB 保留一份旧的
if [ -f "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 1048576 ]; then mv -f "$LOG" "$LOG.old"; fi

# 一条命令停用：systemctl disable --now zzbbs-ui-autodeploy.timer；临时停用：touch $STATE/DISABLED
if [ -e "$STATE/DISABLED" ]; then exit 0; fi

# 同一时间只跑一个实例
exec 9>/var/lock/zzbbs-ui-autodeploy.lock
flock -n 9 || { log "已有实例在运行，跳过"; exit 0; }

# 取远端版本标识；失败返回非 0 且不输出
remote_version() {
  local url=$1 out ver i
  for i in 1 2 3; do
    out=$(curl --socks5 "$PROXY" -sIL -m 30 "$url" 2>/dev/null | tr -d '\r')
    # 最后一个响应必须是 200
    if printf '%s\n' "$out" | grep -E '^HTTP' | tail -1 | grep -q ' 200'; then
      ver=$(printf '%s\n' "$out" | tr 'A-Z' 'a-z' | grep -E '^(etag|content-length|last-modified):' | tail -3 | tr '\n' '|')
      if printf '%s' "$ver" | grep -q 'etag:'; then
        printf '%s' "$ver"; return 0
      fi
    fi
    sleep 3
  done
  return 1
}

# 本机 nginx 健康检查：用户端 / 和管理端 /admin-ui/ 都要 200 且有内容
healthy() {
  local p code size i
  for p in / /admin-ui/; do
    for i in 1 2 3; do
      code=$(curl -sk -o /tmp/zzbbs-health.$$ -w '%{http_code} %{size_download}' -H "Host: $HOST" "https://127.0.0.1$p" 2>/dev/null)
      [ "${code%% *}" = "200" ] && [ "${code##* }" -gt 0 ] && break
      sleep 2
    done
    rm -f /tmp/zzbbs-health.$$
    [ "${code%% *}" = "200" ] && [ "${code##* }" -gt 0 ] || { log "健康检查失败: $p -> $code"; return 1; }
  done
  return 0
}

# check_one 名称 下载地址 部署脚本 目录名
check_one() {
  local name=$1 url=$2 script=$3 dir=$4 ver cur bad
  if ! ver=$(remote_version "$url"); then
    log "WARN [$name] 取不到远端版本(GitHub/代理不可用)，不动线上，下个周期再试"
    return 0
  fi
  cur=$(cat "$STATE/$name.ver" 2>/dev/null || true)
  bad=$(cat "$STATE/$name.bad" 2>/dev/null || true)
  [ "$ver" = "$cur" ] && return 0
  if [ "$ver" = "$bad" ]; then
    log "[$name] 远端版本是之前部署失败的坏版本，跳过: $ver"
    return 0
  fi
  log "[$name] 发现新版本: ${cur:-<首次>} -> $ver"
  if ! bash "$BASE/$script" >> "$LOG" 2>&1; then
    log "ERROR [$name] $script 执行失败（线上未被替换，下个周期重试）"
    return 0
  fi
  if healthy; then
    echo "$ver" > "$STATE/$name.ver"
    log "[$name] 部署成功，入口: $(grep -o 'index-[A-Za-z0-9_-]*\.js' "$BASE/$dir/index.html" | head -1)"
  else
    log "ERROR [$name] 部署后健康检查失败，回滚到 $dir.bak"
    if [ -d "$BASE/$dir.bak" ]; then
      rm -rf "$BASE/$dir.failed"
      mv "$BASE/$dir" "$BASE/$dir.failed" && mv "$BASE/$dir.bak" "$BASE/$dir"
      if healthy; then log "[$name] 已回滚，线上恢复正常；坏版本记为 $ver 不再重试，坏版本文件在 $dir.failed"
      else log "ERROR [$name] 回滚后健康检查仍失败，需要人工处理"; fi
    else
      log "ERROR [$name] 没有 $dir.bak 可回滚，需要人工处理"
    fi
    echo "$ver" > "$STATE/$name.bad"
  fi
}

check_one forum "https://github.com/co2ffee/zzbbs-forum-web/releases/download/deploy-latest/forum-ui.tar.gz" deploy-forum-ui.sh forum-ui
check_one admin "https://github.com/co2ffee/zzbbs-admin-web/releases/download/deploy-latest/admin-ui.tar.gz" deploy-admin-ui.sh admin-ui
exit 0
