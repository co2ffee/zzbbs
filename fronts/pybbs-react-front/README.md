# pybbs-react-front

基于 React 18+ / Vite / TypeScript / MUI 的 pybbs 前端，对接后端 `/api` 接口。

## 开发

```bash
npm install
npm run dev          # http://localhost:3000，默认代理到 http://localhost:8080
PYBBS_BACKEND=http://host:8080 npm run dev   # 指定后端地址
```

## 打包部署

```bash
npm run build        # 产物在 dist/
```

把 `dist/` 交给 Nginx 托管，并将 `/api`、`/common`、`/static`、`/upload` 反向代理到 pybbs 后端；其余路径回退到 `index.html`（SPA）。

```nginx
location / { root /path/to/dist; try_files $uri /index.html; }
location ~ ^/(api|common|static|upload) { proxy_pass http://127.0.0.1:8080; }
```

验证码依赖后端 session（Cookie），因此前端与后端需同域（通过上面的反向代理实现）。
