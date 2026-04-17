# 水鱼牌桌项目说明

[English README](./README.md)

`shuiyu_game` 是一个前后端分离的水鱼扑克牌项目。

## 当前技术栈

- 后端：Spring Boot 3.3.4、MyBatis-Plus 3.5.7、WebSocket、JWT
- 前端：Vue 3、Vite 8、Pinia、Element Plus
- 数据库：MySQL
- Java：17

## 当前环境配置

这份说明已经按当前仓库中的实际配置更新。

- 在线体验地址：`http://47.106.111.217:4173`
- 后端端口：`8088`
- 数据库地址：`jdbc:mysql://127.0.0.1:3306/shuiyu`
- 数据库账号：`root`
- 数据库密码：`123456`
- 前端开发代理目标：`http://127.0.0.1:8088`
- WebSocket 默认地址：`ws://127.0.0.1:8088/ws/game/{roomId}`

说明：

- 后端运行配置位于 `backend/src/main/resources/application.yml`
- 前端 HTTP 基础路径固定为 `/api`
- 前端 WebSocket 默认地址定义在 `frontend/src/useGameSocket.ts`
- 如果后续要部署到公网环境，建议先修改数据库密码和 JWT 密钥

## 在线体验

当前在线体验地址：

- `http://47.106.111.217:4173`

## 目录结构

```text
shuiyu_game/
|- backend/        Spring Boot 后端服务
|- frontend/       Vue 3 前端项目
|- LICENSE
|- README.md
+- README.zh-CN.md
```

## 启动方式

### 1. 准备 MySQL

请先确认 MySQL 已启动，并且已经存在 `shuiyu` 数据库。

当前后端默认使用：

- 主机：`127.0.0.1`
- 端口：`3306`
- 数据库：`shuiyu`
- 用户名：`root`
- 密码：`123456`

### 2. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

启动后地址：

- HTTP 接口：`http://127.0.0.1:8088`
- WebSocket：`ws://127.0.0.1:8088/ws/game/{roomId}`

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

默认情况下，Vite 会把 `/api` 请求代理到 `http://127.0.0.1:8088`。

### 4. 打开页面

打开前端终端输出的本地地址，通常是：

```text
http://127.0.0.1:5173
```

## 构建命令

### 后端打包

```powershell
cd backend
.\mvnw.cmd -q -DskipTests package
```

### 前端构建

```powershell
cd frontend
npm run build
```

## 已实现功能

- 用户注册、登录
- 房间创建、加入、解散
- 玩家坐庄模式、AI 庄家模式
- 发牌、分组、变牌、水鱼与至尊水鱼申报
- 强攻、求走、暗攻、水鱼强攻
- 庄家应对、闲家最终决定
- 单轮结算、全局结算、历史记录查询
- 房间级 WebSocket 实时同步
- 中文牌桌界面与真实牌面资源

## 关键配置文件

### 后端

- 主配置文件：`backend/src/main/resources/application.yml`

### 前端

- Vite 代理：`frontend/vite.config.ts`
- HTTP 请求封装：`frontend/src/api.ts`
- WebSocket 连接：`frontend/src/useGameSocket.ts`

## 备注

- 前端接口基础路径使用 `/api`
- 如果没有配置 `VITE_API_BASE_URL`，前端 WebSocket 会默认连接到 `http://127.0.0.1:8088`
- 当前仓库仍然保留了环境相关配置，正式部署前建议改成环境变量

## 许可证

本仓库采用 Apache License 2.0 许可，详见 [LICENSE](./LICENSE)。
