package com.ghb.ecommerceflashsalesystem.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 *
 * 配合实体上标注的 {@code @TableField(fill = FieldFill.INSERT / INSERT_UPDATE)} 字段生效。
 * 只对标记了 fill 的字段填充（如 SeckillMessageLog.createdAt/updatedAt），其它实体不受影响。
 *
 * 【复盘】曾因缺少本 Handler：SeckillMessageLog 的 createdAt 标注了 fill 却没被填充，
 * INSERT 语句显式带上 NULL → MySQL 报 "Column 'created_at' cannot be null"（strict 模式）。
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
