package com.ghb.ecommerceflashsalesystem.service.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.service.storage.AbstractImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 阿里云 OSS 图片存储（storage.type=oss）
 *
 * 对象键按 uploads/{yyyyMM}/uuid.ext 组织；上传后返回公网访问地址：
 * https://{bucket}.{endpoint}/{objectKey}
 *
 * ⚠️【安全提醒】按 panda 要求 AccessKey 暂以硬编码常量形式落地，便于本地演示切换。
 * 但任何密钥都不应把"真实值"提交进 Git 仓库，生产环境务必改用环境变量 / 配置中心注入并定期轮换；
 * 当前为占位值，部署前替换为真实 AccessKey 并保持 storage.type=oss。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssImageStorageService extends AbstractImageStorage {

    // ---- 阿里云 OSS 账号与端点（硬编码占位，部署前替换为真实值）----
    private static final String ACCESS_KEY_ID = "LTAI5tXXXXXXXXXXXXXXXX";
    private static final String ACCESS_KEY_SECRET = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    private static final String ENDPOINT = "oss-cn-hangzhou.aliyuncs.com";
    private static final String BUCKET = "flash-sale-demo";

    @Override
    public String store(MultipartFile file) {
        validate(file);

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String objectKey = "uploads/" + month + "/" + safeName(file.getOriginalFilename());

        OSS oss = null;
        try {
            oss = new OSSClientBuilder().build(ENDPOINT, ACCESS_KEY_ID, ACCESS_KEY_SECRET);
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(file.getContentType());
            meta.setContentLength(file.getSize());
            try (InputStream in = file.getInputStream()) {
                oss.putObject(new PutObjectRequest(BUCKET, objectKey, in, meta));
            }
            log.info("图片已上传 OSS，objectKey={}", objectKey);
        } catch (Exception e) {
            log.error("OSS 图片上传失败，objectKey={}", objectKey, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "OSS 上传失败：" + e.getMessage());
        } finally {
            if (oss != null) {
                oss.shutdown();
            }
        }
        return "https://" + BUCKET + "." + ENDPOINT + "/" + objectKey;
    }

    @Override
    public String type() {
        return "oss";
    }
}
