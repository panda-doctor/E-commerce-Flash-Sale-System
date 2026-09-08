package com.ghb.ecommerceflashsalesystem.service.storage;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片存储抽象基类：统一"扩展名白名单 / 大小校验 / 安全文件名生成"逻辑
 *
 * 【安全注意】扩展名取自上传文件原名，白名单白名单后才允许写盘/传 OSS，
 * 防止上传 .jsp/.html/.sh 等可执行/可脚本文件；文件名一律由服务端用 UUID 重新生成，
 * 不信任用户原始文件名（防路径穿越）。
 */
@Slf4j
public abstract class AbstractImageStorage implements ImageStorageService {

    protected static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L; // 5MB

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 统一校验：非空 / 类型白名单 / 大小上限 */
    protected void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        String ext = extOf(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "仅支持图片格式：" + String.join("/", ALLOWED_EXT));
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片大小不能超过 5MB");
        }
    }

    protected String extOf(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 生成安全文件名：uuid.ext */
    protected String safeName(String originalFilename) {
        return UUID.randomUUID().toString().replace("-", "") + "." + extOf(originalFilename);
    }
}
