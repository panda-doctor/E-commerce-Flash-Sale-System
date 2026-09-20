package com.ghb.ecommerceflashsalesystem.service.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.service.storage.AbstractImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 阿里云 OSS 图片存储（aliyun.oss.enabled=true 时激活）
 *
 * <p>对象键按 {@code uploads/{yyyyMM}/uuid.ext} 组织；上传后返回公开访问地址
 * {@code {domain}/{objectKey}}（domain 默认 {@code https://{bucket}.{endpoint}}，可换成 CDN/自定义域名）。
 *
 * <p><b>凭据来源（不落 Git）</b>：endpoint / bucket-name / accessKey 均从 application.yaml 的
 * {@code aliyun.oss.*} 注入，密钥支持环境变量 {@code OSS_ACCESS_KEY_ID} / {@code OSS_ACCESS_KEY_SECRET} 覆盖。
 * 任一关键配置缺失时 {@link #store} 抛出带可读提示的业务错误（不发网络请求），避免把真实密钥或
 * SDK 内部堆栈带出给用户。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "aliyun.oss.enabled", havingValue = "true")
public class OssImageStorageService extends AbstractImageStorage {

    @Value("${aliyun.oss.endpoint:}")
    private String endpoint;

    @Value("${aliyun.oss.bucket-name:}")
    private String bucketName;

    @Value("${aliyun.oss.domain:}")
    private String domain;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Override
    public String store(MultipartFile file) {
        validate(file);
        validateConfigured();

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String objectKey = "uploads/" + month + "/" + safeName(file.getOriginalFilename());

        OSS oss = null;
        try {
            oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(mediaTypeOf(file.getOriginalFilename()));
            meta.setContentLength(file.getSize());
            try (InputStream in = file.getInputStream()) {
                oss.putObject(new PutObjectRequest(bucketName, objectKey, in, meta));
            }
            log.info("图片已上传 OSS，bucket={}, objectKey={}", bucketName, objectKey);
        } catch (Exception e) {
            log.error("OSS 图片上传失败，bucket={}, objectKey={}", bucketName, objectKey, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "OSS 图片上传失败，请稍后重试");
        } finally {
            if (oss != null) {
                oss.shutdown();
            }
        }
        return publicBase() + "/" + objectKey;
    }

    @Override
    public String type() {
        return "oss";
    }

    /** 公开访问前缀：优先配置的 domain（可带 CDN/自定义域名），缺省按 bucket.endpoint 拼 */
    private String publicBase() {
        if (isBlank(domain)) {
            return "https://" + bucketName + "." + endpoint;
        }
        String d = domain.trim();
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }

    /** 配置缺失兜底：提示而非 500，也避免用占位/空密钥发起真实请求 */
    private void validateConfigured() {
        if (isBlank(accessKeyId) || isBlank(accessKeySecret)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    "OSS 未配置 AccessKey：请在 application.yaml 设置 aliyun.oss.access-key-id/secret "
                            + "（或环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET），勿将真实密钥提交进 Git");
        }
        if (isBlank(endpoint) || isBlank(bucketName)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    "OSS 未配置 endpoint/bucket：请在 application.yaml 设置 aliyun.oss.endpoint / aliyun.oss.bucket-name");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
