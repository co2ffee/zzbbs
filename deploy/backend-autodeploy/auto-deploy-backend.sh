#!/bin/bash
# 后端自动部署：发现 GitHub deploy-latest 里的 pybbs.jar 有新版本，就替换 jar 并重启，健康检查失败则回滚。
# 只管后端 jar（不碰前端，前端由 auto-deploy-ui.sh 管）。由 systemd timer 每 10 分钟调用，也可手动：deploy-backend-now
# 停用：touch /home/pybbs/autodeploy/BACKEND_DISABLED  或  systemctl disable --now zzbbs-ui-autodeploy.timer
set -u
BASE=/home/pybbs
STATE=$BASE/autodeploy
LOG=$BASE/autodeploy.log
PROXY=127.0.0.1:7891
URL="https://github.com/co2ffee/zzbbs/releases/download/deploy-latest/pybbs.jar"
mkdir -p "$STATE"
log() { echo "[$(date '+%F %T')] $*" >> "$LOG"; }
[ -e "$STATE/BACKEND_DISABLED" ] && exit 0

exec 8>/var/lock/zzbbs-backend-autodeploy.lock
flock -n 8 || { log "[backend] 已有实例在运行，跳过"; exit 0; }
# 人工正在跑 deploy.sh 时不抢
if pgrep -f 'deploy.sh' >/dev/null 2>&1; then log "[backend] 检测到 deploy.sh 正在运行，跳过"; exit 0; fi

remote_version() {
  local out ver i
  for i in 1 2 3; do
    out=$(curl --socks5 "$PROXY" -sIL -m 30 "$URL" 2>/dev/null | tr -d '\r')
    if printf '%s\n' "$out" | grep -E '^HTTP' | tail -1 | grep -q ' 200'; then
      ver=$(printf '%s\n' "$out" | tr 'A-Z' 'a-z' | grep -E '^(etag|content-length|last-modified):' | tail -3 | tr '\n' '|')
      printf '%s' "$ver" | grep -q 'etag:' && { printf '%s' "$ver"; return 0; }
    fi
    sleep 3
  done
  return 1
}

# 等后端健康：/api/site 返回 200
wait_healthy() {
  local i code
  for i in $(seq 1 "${1:-30}"); do
    sleep 2
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8089/api/site || true)
    [ "$code" = "200" ] && return 0
  done
  return 1
}

if ! ver=$(remote_version); then log "WARN [backend] 取不到远端版本，不动线上，下个周期再试"; exit 0; fi
cur=$(cat "$STATE/backend.ver" 2>/dev/null || true)
bad=$(cat "$STATE/backend.bad" 2>/dev/null || true)
[ "$ver" = "$cur" ] && exit 0
[ "$ver" = "$bad" ] && { log "[backend] 远端版本是之前部署失败的坏版本，跳过: $ver"; exit 0; }
log "[backend] 发现新版本: ${cur:-<首次>} -> $ver"

cd "$BASE" || exit 1
rm -f pybbs.jar.new
if ! curl --socks5 "$PROXY" -fsL --max-time 300 -o pybbs.jar.new "$URL" || [ ! -s pybbs.jar.new ] || ! unzip -l pybbs.jar.new >/dev/null 2>&1; then
  log "ERROR [backend] 下载或校验 jar 失败（线上未动，下个周期重试）"; rm -f pybbs.jar.new; exit 0
fi

log "[backend] 停止旧服务，替换 jar，启动"
bash shutdown.sh >> "$LOG" 2>&1; sleep 2
cp -f pybbs.jar "pybbs.jar.prev.$(date +%Y%m%d-%H%M%S)" 2>/dev/null
ls -1t pybbs.jar.prev.* 2>/dev/null | tail -n +4 | xargs -r rm -f   # 只留最近 3 份
mv -f pybbs.jar pybbs.jar.bak 2>/dev/null
mv -f pybbs.jar.new pybbs.jar
bash start.sh >> "$LOG" 2>&1

if wait_healthy 40; then
  echo "$ver" > "$STATE/backend.ver"
  errs=$(grep -c ' ERROR ' log.file 2>/dev/null)
  mig=$(grep -E 'Migrating schema|Successfully applied' log.file | sed 's/\x1b\[[0-9;]*m//g' | sed -E 's/.*DbMigrate - //' | tr '\n' ';' | cut -c1-200)
  log "[backend] 部署成功，健康检查通过；log.file ERROR 行数=$errs；迁移: ${mig:-无}"
else
  log "ERROR [backend] 部署后 80 秒内健康检查未通过，回滚到旧 jar"
  bash shutdown.sh >> "$LOG" 2>&1; sleep 2
  mv -f pybbs.jar pybbs.jar.failed; mv -f pybbs.jar.bak pybbs.jar
  cp -f log.file log.file.failed 2>/dev/null
  bash start.sh >> "$LOG" 2>&1
  if wait_healthy 40; then log "[backend] 已回滚，线上恢复；坏版本记为不再重试，失败日志 log.file.failed"
  else log "ERROR [backend] 回滚后仍不健康（若新版本已应用数据库迁移，旧 jar 可能无法启动），需要人工处理，见 log.file / log.file.failed"; fi
  echo "$ver" > "$STATE/backend.bad"
fi
exit 0
