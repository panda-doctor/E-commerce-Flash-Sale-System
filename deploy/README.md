# 基础设施部署说明（P1 阶段）

> 本目录管理**本地/容器化基础设施**的启停与排查记录，与业务代码分离。
> 当前状态（2026-10-14）：Nacos 为**本机原生安装**（非容器）；**Prometheus + Grafana 已在 T5 用 WSL Docker 落地**（见第四、五节）。

---

## 一、Nacos（注册中心 / 配置中心）

### 1.1 现状（2026-10-14 实测核对）

| 项 | 值 |
|---|---|
| 版本 | **2.5.4** |
| 安装路径 | `D:\nacos-server-2.5.4\nacos` |
| 启动方式 | `startup.cmd -m standalone`（进程参数含 `-Dnacos.standalone=true`） |
| 运行 JDK | `D:\JDK\jdk-21.0.12` |
| 存储 | **standalone 内嵌 Derby**（不依赖 MySQL；`nacos/data/derby-data` 为数据目录） |
| 鉴权 | **未开启**（`auth_enabled=false`），客户端无需配置账号密码 |
| HTTP / 控制台端口 | **8848** |
| 客户端 gRPC 端口 | **9848**（Nacos 2.x 客户端注册走该端口，**必须处于监听状态**） |

### 1.2 启停命令

```powershell
# 启动（standalone）
D:\nacos-server-2.5.4\nacos\bin\startup.cmd -m standalone

# 停止
D:\nacos-server-2.5.4\nacos\bin\shutdown.cmd

# 运行日志（排查启动问题先看它）
D:\nacos-server-2.5.4\nacos\logs\start.out

# 端口核对：应看到 8848 与 9848 均 LISTENING
netstat -ano | findstr ":8848 :9848"
```

控制台地址：<http://localhost:8848/nacos>

### 1.3 开机自启（现状：❌ 未配置）

**核查结论（2026-10-14）**：Windows 服务、计划任务、启动文件夹、注册表 `Run` 项四处均**未发现** Nacos 相关配置，当前需手动启动。

如需自启，任选一种（推荐 a：无需额外工具）：

**a) 任务计划程序**
1. 任务计划程序 → 创建基本任务 → 名称 `Nacos`；
2. 触发器：`当我登录时`（或"计算机启动时"，需勾选"不管用户是否登录都运行"）；
3. 操作：启动程序 `D:\nacos-server-2.5.4\nacos\bin\startup.cmd`，**起始于** `D:\nacos-server-2.5.4\nacos\bin`；
4. 完成后右键任务"运行"验证一次。

**b) 注册为 Windows 服务（nssm / WinSW）**
适合"无人登录也要常驻"的场景，需额外下载工具；注意服务方式启动时 `JAVA_HOME` 必须可用。

> 注意：`startup.cmd` 会新开控制台窗口，且依赖 `JAVA_HOME`（本机 JDK 21）。自启配置后请用 `netstat` 复核 8848/9848。

---

## 二、本项目的注册信息

| 应用 | 服务名 | 说明 |
|---|---|---|
| 单体（模块化单体，P0 产物） | `flash-sale-monolith` | 端口 8081；`spring.application.name` 即服务名 |
| API 网关（P1-1 产物） | `flash-sale-gateway` | 端口 8090；路由 `lb://flash-sale-monolith` |

- 注册地址：`127.0.0.1:8848`（可用环境变量 `NACOS_SERVER_ADDR` 覆盖，见各模块 `application.yaml`）；
- **测试侧关闭注册**：`flash-sale-bootstrap/src/test/resources/application.properties` 中 `spring.cloud.nacos.discovery.enabled=false`（避免 Nacos 未启动时拖慢/报错，也避免测试实例污染服务列表）；
- 查询注册结果（未开鉴权，免登录）：

```powershell
# 服务列表
curl.exe -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10"

# 指定服务的实例与健康状态
curl.exe -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=flash-sale-monolith"
```

---

## 三、常见排查

| 现象 | 先查什么 |
|---|---|
| 控制台打不开 | `netstat -ano \| findstr :8848` 是否有 LISTENING；再看 `logs\start.out` 的启动报错 |
| 应用启动报"注册失败" / 实例不健康 | **9848 是否监听**（客户端 gRPC；只开 8848 会失败）；再看应用侧 `spring.cloud.nacos.discovery.server-addr` |
| 服务列表为空（应用已启动） | 心跳 5s、健康超时 15s，等 10 秒再查；确认应用日志出现 `nacos registry, ... register finished` |
| 实例 IP 不是 127.0.0.1（本机为 `172.21.112.1`，WSL 虚拟网卡） | 本机演示两条地址均可达；若网关转发异常，可在应用侧固定 `spring.cloud.nacos.discovery.ip=127.0.0.1` |

---

## 四、观测栈（T5 ✅ 2026-10-14 落地）

### 4.1 组成与文件

| 组件 | 镜像 | 端口 | 说明 |
|---|---|---|---|
| Prometheus | `prom/prometheus:v2.53.0` | 9090 | 抓取单体/网关 `/actuator/prometheus`；TSDB 保留 15 天；开启 `/-/reload` 热加载 |
| Grafana | `grafana/grafana:11.1.0` | 3000 | 数据源与看板均 **provisioning 自动加载**，无需手点 UI |

```
deploy/
├─ docker-compose.yml                              # obs profile：prometheus + grafana（host 网络）
├─ .env.example                                    # Grafana 管理员账号模板（复制为 .env；.env 已被忽略）
├─ prometheus/prometheus.yml                       # 抓取目标：localhost:8081 / :8090 / :9090（10s 间隔）
└─ grafana/
   ├─ provisioning/datasources/prometheus.yml      # 数据源（固定 uid=prometheus）
   ├─ provisioning/dashboards/dashboards.yml       # 看板加载器（30s 热加载）
   └─ dashboards/flash-sale-observability.json     # 看板：6 面板
```

**看板面板**：抓取目标健康（up）· HTTP QPS（按应用+URI）· HTTP P95 延迟 · JVM 堆内存 · 5xx 错误速率 · 进程 CPU。

### 4.2 启停命令（Windows 侧执行）

```powershell
$compose = '/mnt/d/Project/E-commerceFlashSaleSystem/deploy/docker-compose.yml'

wsl -d redis -- docker compose -f $compose --profile obs up -d     # 启动
wsl -d redis -- docker compose -f $compose ps                      # 状态
wsl -d redis -- docker compose -f $compose --profile obs down      # 停止（保留数据卷）

# 改 prometheus.yml 后热加载
curl.exe -X POST http://127.0.0.1:9090/-/reload
```

> **前置**：抓取目标必须存活 —— 单体 `:8081`、网关 `:8090`（`.\mvnw.cmd -pl flash-sale-bootstrap spring-boot:run` / `-pl flash-sale-gateway spring-boot:run`），否则 targets 显示 `down`。

### 4.3 访问地址（⚠ 用 `127.0.0.1`，不要用 `localhost`）

| 入口 | 地址 | 账号 |
|---|---|---|
| Prometheus | <http://127.0.0.1:9090> | 无 |
| Grafana | <http://127.0.0.1:3000> | `admin` / `flashsale`（见 `deploy/.env`） |
| 默认看板 | Grafana → Dashboards → Flash Sale → **Flash Sale 观测看板（T5）** | — |

**为什么必须写 `127.0.0.1`**：Prometheus/Grafana 是 Go 服务，仅监听 IPv4；Windows 上 `localhost` 优先解析到 IPv6 `::1`，会**连接超时**（实测 `http://[::1]:9090` = 000，`http://127.0.0.1:9090` = 200）。排查"打不开"时先换地址试。

### 4.4 两个环境级结论（2026-10-14 实测）

1. **必须 `network_mode: host`，不能用 bridge + `extra_hosts`**：
   本机 `~/.wslconfig` 为 `networkingMode=mirrored`，bridge 容器访问 Windows 宿主应用全部失败
   （`host.docker.internal` via `host-gateway` → 000；直连 WSL vEthernet IP `172.21.112.1` → 000），
   而 host 网络下容器内 `localhost:8081/8090` 直接 200。故两个服务均 host 网络、**不做 ports 映射**
   （直接监听宿主端口），Grafana 数据源因同命名空间而写 `http://localhost:9090`。
2. **Docker 发布了端口映射的 bridge 容器（如 `my-redis` :6379）在镜像模式下 Windows 可达** ——
   所以本环境唯一需要绕开的方向是"**容器 → Windows 宿主应用**"，其余无需特殊处理。

### 4.5 归属发行版与自启（实测 2026-10-14）

**观测栈装在 WSL 的 `redis` 发行版**（不是默认发行版 `dph`，也不是 `Ubuntu` / `Ubuntu2`）：

| 发行版 | 默认 | 磁盘位置 | Docker / 观测栈 |
|---|---|---|---|
| `dph` | ✅ | `D:\WSL\deepseek_harness` | 无 |
| `Ubuntu` | — | `C:\Users\ghb19\AppData\Local\wsl\{62caa33c-…}` | 无 |
| **`redis`** | — | **`D:\WSL\redis`** | **Docker 29.7.2（`dockerRoot=/var/lib/docker`）+ 观测栈容器 + 项目 Redis（`my-redis` :6379）** |
| `Ubuntu2` | — | `D:\WSL\ubuntu2` | 无 |

- 镜像（约 1 GB）与数据卷全部落在 `D:\WSL\redis`（占 **D 盘**）；
- **所有命令必须带 `-d redis`**：例如 `wsl -d redis -- docker ps -a`；不带 `-d` 会落到默认发行版 `dph`（那里没有 Docker）；
- 容器列表里 Prometheus / Grafana 的 **PORTS 列为空**属正常现象 —— host 网络不做端口映射，服务直接占用宿主 9090/3000。

**自启现状**：`redis` 内 `/etc/wsl.conf` 为 `systemd=true`，`docker.service` 状态 `enabled` + `active`
→ 只要该发行版被唤醒，dockerd 会自动启动、容器因 `restart: unless-stopped` 自动恢复。
但 **WSL 发行版不会随 Windows 开机自动拉起**（默认发行版是 `dph`），重启电脑后需先唤醒：

```powershell
wsl -d redis -- /bin/true      # 唤醒 redis 发行版 → dockerd + 两个容器自动恢复
wsl -d redis -- docker ps -a   # 确认
```

如需免手动：任务计划程序加一条"当我登录时运行" → `wsl.exe -d redis -- /bin/true`（与 Nacos 自启同一套做法）。

---

## 五、观测栈常见排查

| 现象 | 先查什么 |
|---|---|
| Grafana / Prometheus 打不开、超时 | 换 `127.0.0.1` 试（见 4.3）；再 `docker compose ps` 确认容器 Up |
| targets 全部 `down` | 应用是否在跑：`curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8081/actuator/prometheus` |
| 只有某个 job `down` | 该应用未启动，或 `management.endpoints.web.exposure.include` 缺 `prometheus` |
| 看板无数据 | 数据源 URL 是否 `http://localhost:9090`（host 网络）；先在 Prometheus 查询框试 `up` 是否有值 |
| 改配置不生效 | Prometheus：`curl -X POST http://127.0.0.1:9090/-/reload`；看板 JSON 30s 热加载；数据源变更需 `docker compose restart grafana` |
| 容器名冲突 / 端口占用 | 本机 9090、3000 已被本栈占用；如与其它服务冲突，改 compose 内对应端口并同步本文件 |
