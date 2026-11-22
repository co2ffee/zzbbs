#!/bin/bash
# 立刻检查并部署后端（不用等 10 分钟定时器）。用法：deploy-backend-now
LOG=/home/pybbs/autodeploy.log
before=$(wc -l < "$LOG" 2>/dev/null || echo 0)
bash /home/pybbs/auto-deploy-backend.sh
after=$(wc -l < "$LOG" 2>/dev/null || echo 0)
if [ "$after" -le "$before" ]; then echo "没有新版本（后端已是最新）"
else tail -n +$((before + 1)) "$LOG" | grep -E '^\[' | cut -c1-220; fi
