package com.ghb.ecommerceflashsalesystem.controller.admin;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.service.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端图片上传（本地 / 阿里云 OSS 双策略，由 storage.type 决定）
 *
 * POST /api/admin/files/image   multipart 字段名 file
 * 返回：{ url, storageType }，url 可直接作为商品 imageUrl 落库/回填。
 */
@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class AdminFileController {

    private final ImageStorageService imageStorageService;

    @PostMapping("/image")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = imageStorageService.store(file);

        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("storageType", imageStorageService.type());
        return Result.success(data);
    }
}
