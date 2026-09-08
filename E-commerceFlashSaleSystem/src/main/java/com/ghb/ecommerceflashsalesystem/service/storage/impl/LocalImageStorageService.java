package com.ghb.ecommerceflashsalesystem.service.storage.impl;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.service.storage.AbstractImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地磁盘图片存储（storage.type=local，默认）
 *
 * 落盘目录：{storage.local.dir:uploads}/{yyyyMM}/uuid.ext（按月份分目录）
 * 访问：通过 FileStorageWebConfig 把 /uploads/** 映射到本地目录（磁盘读取，零额外成本）。
 * 完整 URL 在 HTTP 请求上下文中拼接当前协议/主机/端口；非请求上下文（如单测）退化为相对路径。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
public class LocalImageStorageService extends AbstractImageStorage {

    @Value("${storage.local.dir:uploads}")
    private String baseDir;

    @Override
    public String store(MultipartFile file) {
        validate(file);
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String fileName = safeName(file.getOriginalFilename());

        Path dir = Paths.get(baseDir, month);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            file.transferTo(target.toAbsolutePath());
            log.info("图片已保存到本地磁盘，path={}", target.toAbsolutePath());
        } catch (IOException e) {
            log.error("本地图片保存失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "图片保存失败，请稍后重试");
        }

        String relative = "/uploads/" + month + "/" + fileName;
        return toPublicUrl(relative);
    }

    @Override
    public String type() {
        return "local";
    }

    /** 相对路径 -> 当前请求主机下的完整 URL */
    private String toPublicUrl(String relative) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // 非 Web 请求上下文（测试直接调用服务层）：退化返回相对路径
            return relative;
        }
        HttpServletRequest req = attrs.getRequest();
        String scheme = req.isSecure() ? "https" : "http";
        int port = req.getServerPort();
        String hostPort = (port == 80 || port == 443) ? req.getServerName() : req.getServerName() + ":" + port;
        return scheme + "://" + hostPort + relative;
    }
}
