# Shuiyu Game

[中文说明](./README.zh-CN.md)

`shuiyu_game` is a front-end / back-end separated Shuiyu poker project.

## Current Stack

- Backend: Spring Boot 3.3.4, MyBatis-Plus 3.5.7, WebSocket, JWT
- Frontend: Vue 3, Vite 8, Pinia, Element Plus
- Database: MySQL
- Java: 17

## Current Environment

This README is aligned with the current repository configuration.

- Online demo: `http://47.106.111.217:4173`
- Backend port: `8088`
- Database URL: `jdbc:mysql://127.0.0.1:3306/shuiyu`
- Database username: `root`
- Database password: `123456`
- Frontend dev proxy target: `http://127.0.0.1:8088`
- WebSocket default target: `ws://127.0.0.1:8088/ws/game/{roomId}`

Important:

- The backend runtime config is stored in `backend/src/main/resources/application.yml`
- The frontend HTTP base path is `/api`
- The frontend WebSocket fallback is defined in `frontend/src/useGameSocket.ts`
- Before deploying to a public environment, update the database password and JWT secret

## Online Demo

You can try the current online build here:

- `http://47.106.111.217:4173`

## Repository Structure

```text
shuiyu_game/
|- backend/        Spring Boot service
|- frontend/       Vue 3 client
|- LICENSE
|- README.md
+- README.zh-CN.md
```

## Quick Start

### 1. Prepare MySQL

Make sure MySQL is running and the `shuiyu` database already exists.

The current backend configuration expects:

- host: `127.0.0.1`
- port: `3306`
- database: `shuiyu`
- username: `root`
- password: `123456`

### 2. Start the Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

After startup, the backend will be available at:

- HTTP API: `http://127.0.0.1:8088`
- WebSocket: `ws://127.0.0.1:8088/ws/game/{roomId}`

### 3. Start the Frontend

```powershell
cd frontend
npm install
npm run dev
```

By default, the Vite dev server proxies `/api` requests to `http://127.0.0.1:8088`.

### 4. Open the Application

Open the Vite local address shown in the terminal, usually:

```text
http://127.0.0.1:5173
```

## Build Commands

### Backend package

```powershell
cd backend
.\mvnw.cmd -q -DskipTests package
```

### Frontend build

```powershell
cd frontend
npm run build
```

## Main Features

- user registration and login
- room creation, join, and dissolve
- player banker mode and AI banker mode
- dealing, grouping, wildcard assignment, and fish declaration
- strong attack, ask-run, hidden attack, and fish attack
- banker response flow and idle final decision flow
- round settlement, global settlement, and history lookup
- room-level WebSocket event synchronization
- Chinese game UI with real card assets

## Configuration Files

### Backend

- Main config: `backend/src/main/resources/application.yml`

### Frontend

- Vite proxy: `frontend/vite.config.ts`
- HTTP client: `frontend/src/api.ts`
- WebSocket client: `frontend/src/useGameSocket.ts`

## Notes

- The frontend HTTP client uses `/api` as the base path
- The WebSocket client falls back to `http://127.0.0.1:8088` if `VITE_API_BASE_URL` is not provided
- This repository currently contains environment-specific configuration values; replace them before production deployment

## License

This repository is licensed under the Apache License 2.0. See [LICENSE](./LICENSE).
