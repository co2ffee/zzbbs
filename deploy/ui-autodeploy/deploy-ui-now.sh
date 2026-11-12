#!/bin/bash
# 立刻检查并部署前端（不用等 10 分钟的定时器）。有新版本就部署，没有就提示“没有新版本”。
# 用法：deploy-ui-now        （服务器上已装到 /usr/local/bin/deploy-ui-now）
LOG=/home/pybbs/autodeploy.log
before=$(wc -l < "$LOG" 2>/dev/null || echo 0)
bash /home/pybbs/auto-deploy-ui.sh
after=$(wc -l < "$LOG" 2>/dev/null || echo 0)
if [ "$after" -le "$before" ]; then
  echo "没有新版本（用户端、管理端都已是最新）"
else
  # 只显示带时间戳的关键行，去掉 curl 进度条
  tail -n +$((before + 1)) "$LOG" | grep -E '^\[' | cut -c1-160
  echo "--- 当前入口: 用户端 $(grep -o 'index-[A-Za-z0-9_-]*\.js' /home/pybbs/forum-ui/index.html | head -1)，管理端 $(grep -o 'index-[A-Za-z0-9_-]*\.js' /home/pybbs/admin-ui/index.html | head -1)"
fi
