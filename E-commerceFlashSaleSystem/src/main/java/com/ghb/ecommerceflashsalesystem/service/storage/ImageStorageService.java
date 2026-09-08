package com.ghb.ecommerceflashsalesystem.service.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 图片存储服务（本地磁盘 / 阿里云 OSS 双策略）
 *
 * 通过配置项 storage.type 切换：local（默认，本地磁盘 + 静态映射）| oss（阿里云 OSS）。
 * 具体实现类分别标注 @ConditionalOnProperty，同一时刻只有一个 bean 生效，
 * 业务侧只需注入本接口，不感知底层存储（策略模式）。
 */
public interface ImageStorageService {

    /**
     * 保存上传的图片，返回可直接访问的完整 URL
     *
     * @param file 上传文件（multipart），内部做扩展名白名单 + 大小校验
     * @return 图片访问 URL
     */
    String store(MultipartFile file);

    /**
     * 当前存储策略标识（local / oss），用于接口返回与排查
     */
    String type();
}
