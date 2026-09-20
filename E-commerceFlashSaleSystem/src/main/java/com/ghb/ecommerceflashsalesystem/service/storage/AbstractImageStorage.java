package com.ghb.ecommerceflashsalesystem.service.storage;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片存储抽象基类：统一"扩展名白名单 / 文件头校验 / 大小校验 / 安全文件名生成"逻辑
 *
 * 【安全注意】文件扩展名和文件头均须匹配图片格式，才允许写盘/传 OSS，防止将
 * .jsp/.html/.sh 等内容伪装成图片；文件名一律由服务端用 UUID 重新生成，
 * 不信任用户原始文件名（防路径穿越）。
 */
@Slf4j
public abstract class AbstractImageStorage implements ImageStorageService {

    protected static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L; // 5MB

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 统一校验：非空 / 扩展名白名单 / 文件头匹配 / 大小上限 */
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
        validateSignature(file, ext);
    }

    /**
     * 仅凭文件名不能判断真实类型。此处校验常见图片的固定文件头，且要求其与扩展名一致。
     * 该校验用于拒绝伪装的脚本或文本文件；更严格的病毒扫描应在对象存储或网关层完成。
     */
    private void validateSignature(MultipartFile file, String ext) {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        } catch (IOException e) {
            log.warn("读取上传文件失败", e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "无法读取上传文件");
        }

        if (!hasExpectedSignature(header, read, ext)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件内容与图片格式不匹配");
        }
    }

    private boolean hasExpectedSignature(byte[] bytes, int length, String ext) {
        return switch (ext) {
            case "png" -> length >= 8
                    && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "jpg", "jpeg" -> length >= 3
                    && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF;
            case "gif" -> length >= 6
                    && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
            case "bmp" -> length >= 2 && bytes[0] == 'B' && bytes[1] == 'M';
            case "webp" -> length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    /** 根据已验证的扩展名生成响应头，不能信任客户端提交的 Content-Type。 */
    protected String mediaTypeOf(String filename) {
        return switch (extOf(filename)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
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
