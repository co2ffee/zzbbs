# 后端自动合并 + 自动部署

链路：`claude/*` 分支的 PR（非 draft）→ test + build + GitGuardian 通过 → 自动合并 → 触发 `release-jar.yml` → 服务器每 10 分钟检查一次，有新 jar 就替换并重启。

- 自动合并：`.github/workflows/auto-merge.yml`。改了 `.github/` 的 PR 不自动合并。停用：删文件或设 Actions 变量 `AUTO_MERGE_DISABLED=true`。
- 服务器脚本：`/home/pybbs/auto-deploy-backend.sh`（由 `zzbbs-ui-autodeploy.service` 的 `ExecStartPost` 调用）。立即部署：`deploy-backend-now`。
- 停用服务器端：`touch /home/pybbs/autodeploy/BACKEND_DISABLED`。
- 失败回滚：80 秒内 `/api/site` 不是 200 就换回上一版 jar；坏版本记入 `autodeploy/backend.bad` 不再重试。旧 jar 保留最近 3 份 `pybbs.jar.prev.*`。
- 日志：`/home/pybbs/autodeploy.log`。
- **风险**：迁移（Flyway）一旦应用，回滚旧 jar 可能因迁移记录不识别而无法启动，这种情况需要人工处理；服务器上没有 mysqldump，没有自动数据库备份。迁移请写成可重复、向前兼容的形式。
