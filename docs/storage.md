# 文件存储指南（本地磁盘 / 阿里云 OSS 双策略）

> 适用范围：商品主图上传。`aliyun.oss.enabled=true` 走阿里云 OSS（默认）；`false` 走本地磁盘。
> 相关代码：`service/storage/`（策略接口 + 双实现）、`controller/admin/AdminFileController.java`、
> `config/FileStorageWebConfig.java`。

## 一、架构与切换

```text
POST /api/admin/files/image (multipart: file)
        │  ImageStorageService（接口，业务只依赖它，不感知底层）
        ├─ OssImageStorageService       ← aliyun.oss.enabled=true（默认）
        │     @ConditionalOnProperty(havingValue = "true")
        │     上传至 OSS，返回 {domain}/uploads/{yyyyMM}/uuid.ext
        └─ LocalImageStorageService     ← aliyun.oss.enabled=false（或未配置）
              @ConditionalOnProperty(havingValue = "false", matchIfMissing = true)
              落盘 {storage.local.dir}/{yyyyMM}/uuid.ext，/uploads/** 由 FileStorageWebConfig 映射为静态资源
```

- 校验与命名在抽象基类 `AbstractImageStorage`：扩展名白名单（png/jpg/jpeg/gif/webp）、5MB 上限、
  UUID 安全命名（防脚本文件与路径穿越）。
- 同一时刻只有一个实现生效（`@ConditionalOnProperty`），切换只改配置、不动代码。

## 二、配置

`application.yaml`：

```yaml
aliyun:
  oss:
    enabled: ${OSS_ENABLED:true}                                  # true=OSS / false=本地磁盘
    endpoint: ${OSS_ENDPOINT:oss-cn-beijing.aliyuncs.com}        # 如北京地域
    access-key-id: ${OSS_ACCESS_KEY_ID:}                          # 密钥务必走环境变量，勿提交 Git
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    bucket-name: ${OSS_BUCKET_NAME:panda-tea}
    domain: ${OSS_DOMAIN:https://panda-tea.oss-cn-beijing.aliyuncs.com}   # 可换成 CDN/自定义域名

storage:
  local:
    dir: uploads   # enabled=false 时的本地落盘目录
```

### 环境变量注入密钥（推荐）

```bash
# Linux / macOS
export OSS_ACCESS_KEY_ID=你的AccessKeyId
export OSS_ACCESS_KEY_SECRET=你的AccessKeySecret
# Windows PowerShell
$env:OSS_ACCESS_KEY_ID = "你的AccessKeyId"      # 持久化用 setx（注意不要写成 %VAR% 自引用）
```

⚠ 若环境变量被误设成 `%OSS_ACCESS_KEY_ID%` 之类的字面占位，上传会收到阿里云 `InvalidAccessKeyId`。

## 三、验证

本地磁盘模式：

```bash
curl -F "file=@demo.png" http://localhost:8081/api/admin/files/image
# → { "code":0, "data": { "url": "http://localhost:8081/uploads/202609/xxxx.png", "storageType": "local" } }
```

OSS 模式（配置真实密钥后）：

```bash
curl -F "file=@demo.png" http://localhost:8081/api/admin/files/image
# → { "code":0, "data": { "url": "https://panda-tea.oss-cn-beijing.aliyuncs.com/uploads/202609/xxxx.png", "storageType": "oss" } }
```

管理控制台「商品主图管理」上传回填 `image_url` 即走当前策略。

## 四、本地 ↔ OSS 一键切换

| 场景 | `aliyun.oss.enabled` | 额外要求 |
| --- | --- | --- |
| 本地开发 / 演示 | `false` | 无 |
| 外网演示 / 生产 | `true`（默认） | 设置 OSS 三项环境变量（或 yaml 直填，勿提交 Git） |

## 五、安全与运维提醒

1. **密钥轮换**：定期更换 RAM Key；若泄露立即禁用。仓库内任何占位 Key 都必须替换后再用；
   `application.yaml` 中不要写死真实密钥，用 `${OSS_ACCESS_KEY_ID:}` 这类环境变量占位。
2. **对象键按月分目录**（`uploads/{yyyyMM}/`），便于冷备与生命周期清理。
3. **Bucket 权限最小化**：上传走 RAM 子账号授权，不建议公开写；展示域名可换 CDN/自定义域名。
4. `spring.servlet.multipart` 上限（6MB）大于服务内 5MB 校验，预留 multipart 余量，勿反向收紧导致上传假失败。
