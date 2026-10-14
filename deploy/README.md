# 基础设施部署说明（P1 阶段）

> 本目录管理**本地/容器化基础设施**的启停与排查记录，与业务代码分离。
> 当前状态：Nacos 为**本机原生安装**（非容器）；Prometheus + Grafana 计划在 T5 用 WSL Docker 落地（届时本目录新增 `docker-compose.yml`）。

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

## 四、观测栈（T5 待落地）

- 方案：`redis` 发行版内的 WSL Docker（Engine 29.7.2 / Compose v5.5.0）起 **Prometheus + Grafana**；
- 抓取目标：单体 `:8081/actuator/prometheus`、网关 `:8090/actuator/prometheus`（容器访问 Windows 宿主需 `extra_hosts: ["host.docker.internal:host-gateway"]`）；
- 落地后本目录将新增：`docker-compose.yml`（`obs` profile）、`prometheus/prometheus.yml`、`.env.example`。
