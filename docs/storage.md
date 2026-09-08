# 文件存储指南（本地磁盘 / 阿里云 OSS 双策略）

> 适用范围：商品主图上传。`aliyun.oss.enabled=true` 走阿里云 OSS；`false` 走本地磁盘（默认）。
> 相关代码：`service/storage/`（策略接口 + 双实现）、`controller/admin/AdminFileController.java`、
> `config/FileStorageWebConfig.java`。

> 注意：上述代码路径均相对于后端目录 `E-commerceFlashSaleSystem/`。默认使用本地磁盘，部署到
> OSS 时再将 `OSS_ENABLED` 设为 `true` 并提供 AccessKey。

## 一、架构与切换

```text
POST /api/admin/files/image (multipart: file)
        │  ImageStorageService（接口，业务只依赖它，不感知底层）
        ├─ OssImageStorageService       ← aliyun.oss.enabled=true
        │     @ConditionalOnProperty(havingValue = "true")
        │     上传至 OSS，返回 {domain}/uploads/{yyyyMM}/uuid.ext
        └─ LocalImageStorageService     ← aliyun.oss.enabled=false（默认，或未配置）
              @ConditionalOnProperty(havingValue = "false", matchIfMissing = true)
              落盘 {storage.local.dir}/{yyyyMM}/uuid.ext，/uploads/** 由 FileStorageWebConfig 映射为静态资源
```

- 校验与命名在抽象基类 `AbstractImageStorage`：扩展名白名单（png/jpg/jpeg/gif/webp/bmp）、
  与扩展名匹配的文件头校验、5MB 上限、
  UUID 安全命名（防脚本文件与路径穿越）。
- 同一时刻只有一个实现生效（`@ConditionalOnProperty`），切换只改配置、不动代码。

上传接口会校验常见图片的文件头，不能仅靠改后缀绕过；仍应只向可信的管理端开放。
如需对外开放上传，还应在网关或对象存储侧增加鉴权、限流、病毒扫描等防护。

## 二、配置

`application.yaml`：

```yaml
aliyun:
  oss:
    enabled: ${OSS_ENABLED:false}                                 # true=OSS / false=本地磁盘（默认）
    endpoint: ${OSS_ENDPOINT:oss-cn-beijing.aliyuncs.com}        # 如北京地域
    access-key-id: ${OSS_ACCESS_KEY_ID:}                          # 密钥务必走环境变量，勿提交 Git
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    bucket-name: ${OSS_BUCKET_NAME:panda-tea}
    domain: ${OSS_DOMAIN:https://panda-tea.oss-cn-beijing.aliyuncs.com}   # 可换成 CDN/自定义域名

storage:
  local:
    dir: uploads   # enabled=false 时的本地落盘目录；相对路径基于后端启动工作目录
```

### 环境变量注入密钥（推荐）

```bash
# Linux / macOS
export OSS_ACCESS_KEY_ID=你的AccessKeyId
export OSS_ACCESS_KEY_SECRET=你的AccessKeySecret
# Windows PowerShell
$env:OSS_ACCESS_KEY_ID = "你的AccessKeyId"
$env:OSS_ACCESS_KEY_SECRET = "你的AccessKeySecret"
# 持久化可使用 setx；重新打开终端并重启后端后才会生效。
# 不要把变量值设为 %OSS_ACCESS_KEY_ID% 这类字面占位。
```

⚠ 若环境变量被误设成 `%OSS_ACCESS_KEY_ID%` 之类的字面占位，上传会收到阿里云 `InvalidAccessKeyId`。

## 三、验证

本地磁盘模式：

```bash
curl -F "file=@demo.png" http://localhost:8081/api/admin/files/image
# → { "code":0, "message":"操作成功", "data": { "url": "http://localhost:8081/uploads/202609/xxxx.png", "storageType": "local" }, ... }
```

在 Windows PowerShell 中，`curl` 可能是 `Invoke-WebRequest` 的别名，不能使用上面的 `-F` 参数；
请使用 `curl.exe -F "file=@demo.png" http://localhost:8081/api/admin/files/image`。

本地文件实际写入后端进程工作目录下的 `uploads/{yyyyMM}/`，并由 `/uploads/**` 映射读取。
若后端运行在反向代理之后，当前实现按收到的请求主机和端口拼接返回 URL；请让代理正确转发
`Host`，或在部署前验证上传回显的 URL 能被外部访问。

OSS 模式（配置真实密钥后）：

```bash
curl -F "file=@demo.png" http://localhost:8081/api/admin/files/image
# → { "code":0, "message":"操作成功", "data": { "url": "https://panda-tea.oss-cn-beijing.aliyuncs.com/uploads/202609/xxxx.png", "storageType": "oss" }, ... }
```

返回的是可直接访问的对象 URL。请确保 Bucket 的读策略或配置的 CDN/自定义域名允许读取该对象；
若 Bucket 为私有读，当前实现不会生成签名 URL，前端将无法直接展示图片。

管理控制台「商品主图管理」上传回填 `image_url` 即走当前策略。

## 四、本地 ↔ OSS 一键切换

| 场景 | `aliyun.oss.enabled` | 额外要求 |
| --- | --- | --- |
| 本地开发 / 演示 | `false` | 无 |
| 外网演示 / 生产 | `true` | 至少设置 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`；按实际 Bucket 覆盖 endpoint、bucket、domain |

## 五、安全与运维提醒

1. **密钥轮换**：定期更换 RAM Key；若泄露立即禁用。仓库内任何占位 Key 都必须替换后再用；
   `application.yaml` 中不要写死真实密钥，用 `${OSS_ACCESS_KEY_ID:}` 这类环境变量占位。
2. **对象键按月分目录**（`uploads/{yyyyMM}/`），便于冷备与生命周期清理。
3. **Bucket 权限最小化**：上传走仅具备目标前缀写入权限的 RAM 子账号，绝不授予公开写；若前端需要直出图片，单独配置只读访问或 CDN 读取策略。
4. `spring.servlet.multipart.max-file-size` 为 6MB，而服务内限制为 5MB：5–6MB 的文件会进入业务校验并返回可读错误；`max-request-size` 为 8MB，覆盖单文件请求及表单开销。调整时应保持框架限制不小于业务限制。
