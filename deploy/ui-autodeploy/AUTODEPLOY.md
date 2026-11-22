# 前端自动部署（服务器）

前端（用户端 `/`、管理端 `/admin-ui/`）合并到 `main`、CI 更新 `deploy-latest` release 后，服务器每 10 分钟检查一次，发现新版本就自动上线。**只管前端静态文件，后端仍手动 `deploy.sh`（重启前先问用户）。**

> 合并前端 PR 前必须确认 CI 全绿——合并后约 10 分钟内就会上线。

## 组成（都在服务器上）
| 文件 | 作用 |
| --- | --- |
| `/home/pybbs/auto-deploy-ui.sh` | 主脚本：查版本 → 调用 `deploy-forum-ui.sh` / `deploy-admin-ui.sh` → 健康检查 → 失败回滚 |
| `/etc/systemd/system/zzbbs-ui-autodeploy.{service,timer}` | 每 10 分钟触发（开机 3 分钟后首次），oneshot，不常驻 |
| `/usr/local/bin/deploy-ui-now`（源码 `deploy-ui-now.sh`） | 手动立刻部署：调用主脚本并只显示这次的日志 |
| `/home/pybbs/autodeploy/{forum,admin}.ver` | 当前已部署版本标识 |
| `/home/pybbs/autodeploy/{forum,admin}.bad` | 部署失败过的坏版本（不会重复尝试） |
| `/home/pybbs/autodeploy.log`（超 1MB 轮转为 `.old`） | 日志 |

## 行为
- 版本判断：对 GitHub 下载地址做 HEAD，取 `etag + content-length + last-modified`（走 `socks5 127.0.0.1:7891`）。**不用 GitHub API**：代理出口共用，未登录额度已被用光（返回 403）。
- 版本没变：无输出。取不到版本（GitHub/代理不可用）：只记 `WARN`，不动线上，下个周期再试。
- 部署后检查本机 nginx：`https://127.0.0.1/` 和 `/admin-ui/`（`Host: dljdlj.top`）都返回 200 且有内容。失败则把当前目录挪成 `*.failed`、恢复 `*.bak`，并把该版本记入 `.bad`。
- `deploy-*-ui.sh` 本身失败（下载中断等）：线上没被替换，不记坏版本，下个周期重试。
- `flock` 保证同一时间只有一个实例。

## 日常操作（在服务器上）
```bash
deploy-ui-now                                        # 立刻检查并部署前端，不用等 10 分钟（1~几秒；没有新版本会提示）
tail -50 /home/pybbs/autodeploy.log                  # 看日志（关键字：发现新版本 / 部署成功 / ERROR / WARN）
systemctl list-timers | grep zzbbs                   # 下次触发时间
bash /home/pybbs/auto-deploy-ui.sh                   # 立即检查一次
```

## 停用 / 恢复
```bash
systemctl disable --now zzbbs-ui-autodeploy.timer    # 彻底停用（恢复手动部署）
systemctl enable --now zzbbs-ui-autodeploy.timer     # 重新启用
touch /home/pybbs/autodeploy/DISABLED                # 临时停用（脚本直接退出）；rm 该文件恢复
```

## 手动回滚
上一版保留在 `forum-ui.bak` / `admin-ui.bak`：
```bash
cd /home/pybbs && systemctl disable --now zzbbs-ui-autodeploy.timer   # 先停，否则可能又被部署回来
mv forum-ui forum-ui.bad && mv forum-ui.bak forum-ui                  # 管理端同理 admin-ui
```
要让自动部署继续跳过那个版本：`cp autodeploy/forum.ver autodeploy/forum.bad`（先确认 `.ver` 是坏版本）。

## 出问题怎么看
- 一直没部署：看日志有没有 `WARN 取不到远端版本`；手动 `curl --socks5 127.0.0.1:7891 -sIL <下载地址>` 看是否 200。
- 部署了但页面不对：浏览器加 `?r=时间戳` 绕过缓存（`index.html` 没有 Cache-Control）。
- `ERROR … 需要人工处理`：回滚后健康检查仍失败，通常是 nginx 本身的问题，查 `systemctl status nginx`。
