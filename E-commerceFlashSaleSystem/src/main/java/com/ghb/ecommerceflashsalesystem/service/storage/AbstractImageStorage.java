package com.ghb.ecommerceflashsalesystem.service.storage;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 图片存储抽象基类：统一"内容魔数探测 / 大小校验 / 安全文件名生成"逻辑
 *
 * <p><b>E6 策略（魔数优先，2026-09-09）</b>：早期实现要求「扩展名白名单 + 扩展名与文件头
 * 一致」，会把微信/网页另存等「扩展名与实际格式不符」的合法图片误拒（如 .png 实为 JPEG）。
 * 现改为：
 * <ol>
 *   <li><b>文件头魔数是唯一权威</b>——内容必须是白名单图片格式（jpg/png/gif/bmp/webp），
 *       文件名扩展名不再作为拦截依据（文件名本就不可信）；</li>
 *   <li>存储文件名与响应 Content-Type <b>均按探测出的真实格式</b>生成（uuid.真实扩展名），
 *       不信任客户端 Content-Type，也不沿用过时扩展名（防脚本伪装、防类型错乱）；</li>
 * </ol>
 * 效果：伪装脚本/文本仍被拒（内容非图片魔数），真实图片不再因命名不规范被误拒。
 */
@Slf4j
public abstract class AbstractImageStorage implements ImageStorageService {

    protected static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L; // 5MB

    /** 读取的文件头长度：需覆盖 WEBP 的 RIFF(4) + size(4) + WEBP(4) = 12 字节 */
    private static final int HEADER_LEN = 12;

    /**
     * 统一校验并探测真实格式：非空 / 大小上限 / 文件头魔数 ∈ 白名单图片格式。
     *
     * @return 探测出的规范扩展名（png / jpg / gif / bmp / webp），供存储命名与 Content-Type 使用
     */
    protected String validateAndDetectExt(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片大小不能超过 5MB");
        }

        byte[] header = new byte[HEADER_LEN];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        } catch (IOException e) {
            log.warn("读取上传文件失败", e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "无法读取上传文件");
        }

        String ext = detectImageExt(header, read);
        if (ext == null) {
            // 内容不是白名单图片（伪装脚本/文本、或其它格式）→ 拒绝；文案可读、不暴露细节
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "文件内容不是受支持的图片格式（仅 jpg/jpeg/png/gif/webp/bmp）");
        }
        return ext;
    }

    /**
     * 按文件头魔数探测图片真实格式（与文件名扩展名无关）。
     * 字节数不足即判定不匹配；返回规范扩展名，null 表示非白名单图片。
     */
    private String detectImageExt(byte[] b, int length) {
        if (length >= 8
                && b[0] == (byte) 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (length >= 3 && b[0] == (byte) 0xFF && b[1] == (byte) 0xD8 && b[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (length >= 6
                && b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "gif";
        }
        if (length >= 2 && b[0] == 'B' && b[1] == 'M') {
            return "bmp";
        }
        if (length >= HEADER_LEN
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /** 按探测出的真实扩展名生成固定 Content-Type（不反射客户端提交值） */
    protected String mediaTypeOf(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    /** 生成安全文件名：uuid.{真实格式扩展名}（扩展名由探测得出，防脚本伪装与路径穿越） */
    protected String safeName(String ext) {
        return UUID.randomUUID().toString().replace("-", "") + "." + ext;
    }
}
