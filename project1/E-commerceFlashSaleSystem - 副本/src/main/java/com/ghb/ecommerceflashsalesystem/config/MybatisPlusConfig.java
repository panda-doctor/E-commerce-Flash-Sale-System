package com.ghb.ecommerceflashsalesystem.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册乐观锁拦截器。
 *
 * <p>配合实体上的 {@link com.baomidou.mybatisplus.annotation.Version} 字段生效：
 * 并发更新活动等聚合行时，乐观锁使"最后提交覆盖先提交"变成"先提交成功、后提交更新 0 行"，
 * 从源头避免管理端多窗口编辑互相覆盖（审计 R4 关联项）。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
