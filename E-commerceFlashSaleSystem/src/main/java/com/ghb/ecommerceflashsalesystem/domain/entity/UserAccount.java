package com.ghb.ecommerceflashsalesystem.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccount {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String accountName;
    private String passwordHash;
    private String displayName;
    /** 0=禁用，1=正常，2=已软删除。 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
