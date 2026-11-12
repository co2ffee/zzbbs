# 前端自动部署：安装说明（给能 SSH 到服务器的人/AI）

目标：前端（用户端 `/`、管理端 `/admin-ui/`）合并到 `main` 后，**服务器自己在约 10 分钟内拉最新发布包并上线**，不用再手动跑 `deploy-forum-ui.sh` / `deploy-admin-ui.sh`。**后端不在范围内**（要重启，仍然手动 `deploy.sh`，重启前先问用户）。

## 它做什么

`auto-deploy-ui.sh` 由 systemd timer 每 10 分钟运行一次：

1. 通过 mihomo 代理查 GitHub API：两个前端仓库 `deploy-latest` release 里 `forum-ui.tar.gz` / `admin-ui.tar.gz` 的 `id + updated_at`（重新上传会变）。
2. 和上次部署成功时记下的对比（`/home/pybbs/.autodeploy/*.state`）。没变就什么都不做。
3. 变了：执行**现有的** `/home/pybbs/deploy-forum-ui.sh` 或 `deploy-admin-ui.sh`（下载 → 校验 → 原子替换，保留 `.bak`）。
4. 健康检查：请求本机 nginx（`/` 和 `/admin-ui/`）要返回 200。**失败就自动回滚到 `.bak`**，把坏版本文件留在 `forum-ui.failed` / `admin-ui.failed`，记为坏版本，**同一个坏版本不会重复尝试**，等下一个新版本。
5. 日志：`/home/pybbs/autodeploy.log`（超过 1MB 自动截断）和 `journalctl -u zzbbs-ui-autodeploy`。

GitHub 访问失败（网络、限流）只记 WARN，不动线上，下个周期再试。同一时间只会跑一个实例（flock）。

## 安装步骤

前提：`/home/pybbs/` 下已有 `deploy-forum-ui.sh`、`deploy-admin-ui.sh` 且能正常运行；服务器有 `systemd`、`curl`、`python3`、`flock`（util-linux）。先确认：

```bash
systemctl --version | head -1; command -v curl python3 flock
```

缺 `python3` 或 `flock` 先停下来，在 PR #3 里告诉写代码的 Claude，不要自己改脚本。

### 1. 放文件

本仓库合并到 `master` 后，从仓库取（走代理）：

```bash
cd /home/pybbs
B=https://raw.githubusercontent.com/co2ffee/zzbbs/master/deploy
curl --socks5 127.0.0.1:7891 -fL -o auto-deploy-ui.sh $B/auto-deploy-ui.sh
curl --socks5 127.0.0.1:7891 -fL -o /etc/systemd/system/zzbbs-ui-autodeploy.service $B/zzbbs-ui-autodeploy.service
curl --socks5 127.0.0.1:7891 -fL -o /etc/systemd/system/zzbbs-ui-autodeploy.timer $B/zzbbs-ui-autodeploy.timer
chmod +x auto-deploy-ui.sh
```

用 `cat` 看一遍下载下来的内容是不是脚本（不是 HTML 错误页）。

### 2. 先确认健康检查能通过（不改任何东西）

脚本用 `Host: dljdlj.top` 请求本机 `https://127.0.0.1`（跳过证书校验），要求返回 200：

```bash
curl -sk -o /dev/null -w '%{http_code}\n' -H 'Host: dljdlj.top' https://127.0.0.1/
curl -sk -o /dev/null -w '%{http_code}\n' -H 'Host: dljdlj.top' https://127.0.0.1/admin-ui/
```

两个都应该是 `200`。不是 200（例如 301、404）**先别启用**，把返回码和 nginx 相关配置贴到 PR #3，由写代码的 Claude 调整脚本里的健康检查地址。

### 3. 手动跑一次

```bash
bash /home/pybbs/auto-deploy-ui.sh; echo "exit=$?"
cat /home/pybbs/autodeploy.log
```

**第一次运行没有状态记录，会把两个前端都重新部署一遍**（等同于手动执行两个 deploy 脚本，是安全的，会保留 `.bak`）。预期看到两行 `部署成功`。再跑第二次应该什么都不输出（已是最新）。

### 4. 启用定时器

```bash
systemctl daemon-reload
systemctl enable --now zzbbs-ui-autodeploy.timer
systemctl list-timers | grep zzbbs
```

### 5. 验证

- `systemctl list-timers` 里能看到下一次触发时间；
- 下一次有前端 PR 合并后，等约 10 分钟，`tail /home/pybbs/autodeploy.log` 应该出现 `发现新版本 ... 部署成功`，浏览器里入口 js 文件名（`index-xxxx.js`）变成新的。把第一次自动部署的日志贴到 PR #3 确认。

## 之后的流程变化

- 前端：合并 PR → CI 构建发布 → 最多约 10 分钟自动上线（构建 + 定时周期）。你只需要**确认入口 js 文件名变了、跑复测**，不用手动部署。想立刻上线可以直接 `systemctl start zzbbs-ui-autodeploy.service`。
- 后端：不变，手动 `deploy.sh`，重启前先问用户。
- 注意：前端合并后会自动上线，所以合并前一定要看 CI 全绿。

## 排查与回滚

- 看日志：`tail -50 /home/pybbs/autodeploy.log`；`journalctl -u zzbbs-ui-autodeploy -n 50`。
- 自动回滚后的现场：`/home/pybbs/forum-ui.failed`（或 `admin-ui.failed`）是坏版本，线上已经是上一版。排查完删掉；要重新尝试同一个版本，删 `/home/pybbs/.autodeploy/forum-ui.bad`。
- 手动回滚：`cd /home/pybbs && rm -rf forum-ui && mv forum-ui.bak forum-ui`。回滚后如果不想被自动覆盖，先停定时器。
- 停用：`systemctl disable --now zzbbs-ui-autodeploy.timer`。
- GitHub API 限流：日志里反复出现 `查询 GitHub release 失败 ... 403`。代理出口 IP 被别人用满了（未登录每小时 60 次，本脚本每小时只用 12 次）。解决：建一个不勾选任何权限的 GitHub 令牌，写进 `/etc/zzbbs-autodeploy.env`（`GITHUB_TOKEN=xxx`，`chmod 600`），服务会自动读取。**令牌不要贴到 PR 或聊天里。**
