# Node.js version for bares

This project is built with Quasar v2 + @quasar/app-vite v1 and Vite v2.9.

To avoid known resolution issues on newer Node.js runtimes, use Node.js 20.x (or 18.x).

Quick setup with nvm:

1) Install Node.js 20 and use it:

```
nvm install 20
nvm use 20
```

2) Reinstall deps and run:

```
cd frontend
rm -rf node_modules package-lock.json
npm i
npm run dev
```

You can also use the provided `.nvmrc` in `frontend/` to auto-select the right Node version:

```
cd frontend
nvm use
```

