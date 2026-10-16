package com.ghb.ecommerceflashsalesystem.order.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 双数据源装配（P4 Day 4，**过渡期桥**，auth 域服务化后整类删除）。
 *
 * <p><b>为什么需要它</b>：order 应用在 Day 4 把权威库切到自己的 schema {@code flash_sale_order}，
 * 但 auth 域尚未服务化 —— {@code user_account} / {@code user_session} **只存在于旧库**
 * {@code flash_sale}。而 auth-core 的 {@code ApiAccessInterceptor}（本应用取
 * {@code AUTHENTICATED_USER_ID} 的地方）必须查 {@code user_session} 校验令牌 →
 * 单数据源下必然报 {@code Table 'flash_sale_order.user_session' doesn't exist}（实测如此）。</p>
 *
 * <p><b>方案</b>：主数据源（{@code @Primary}；{@code spring.datasource}）= {@code flash_sale_order}
 * 服务 {@code order.mapper} / {@code analytics.mapper}；auth 专用数据源（{@code module.auth-db}）
 * = 旧库，只服务 {@code user.mapper} 与它自己的 {@code SqlSessionFactory}。</p>
 *
 * <p><b>⚠️ 一个必须记住的坑（本日实测踩到）</b>：MyBatis-Plus 的自动配置
 * （{@code MybatisPlusAutoConfiguration.sqlSessionFactory}）带 {@code @ConditionalOnMissingBean}
 * —— 只要我们自己声明了**任意一个** {@code SqlSessionFactory}，它就整体退让。于是未指定
 * {@code sqlSessionFactoryRef} 的 {@code @MapperScan}（order + analytics）会按类型落到**唯一存在的**
 * 那个工厂（auth 工厂）上 → **服务照常启动、订单查询静默读旧库副本**（用"只改权威库看应用跟谁走"
 * 或 {@code information_schema.processlist} 的 db 归属才能发现）。因此这里把**主工厂也显式声明**，
 * 并用约定名 {@code sqlSessionFactory} 让 MyBatis-Spring 按名字解析。</p>
 *
 * <p><b>取舍与待办</b>：① auth 的写操作走第二条连接、不参与主数据源事务（单语句写，可接受）；
 * ② 两个工厂都未接 {@code mybatis-plus.global-config}（实体上的 {@code @TableId} 已显式声明策略，
 * 影响可忽略）；③ auth 域服务化（或"瘦用户会话守卫"下沉 infra）后删除本类与 {@code module.auth-db}
 * 段，同问题在 {@code flash-sale-seckill} 应用里用"整库指旧库"的粗办法处理，届时一并收敛。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.ghb.ecommerceflashsalesystem.user.mapper",
        sqlSessionFactoryRef = "authSqlSessionFactory")
@Slf4j
public class DataSourceConfig {

    /** 主数据源：order 自己的 schema（权威） */
    @Primary
    @Bean(name = "dataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    /** auth 专用数据源：旧库（未拆域的 user_account / user_session 在那里） */
    @Bean(name = "authDataSource")
    @ConfigurationProperties(prefix = "module.auth-db")
    public DataSource authDataSource() {
        return DataSourceBuilder.create().build();
    }

    /** 主 SqlSessionFactory（order.mapper + analytics.mapper）；名字必须是约定名 sqlSessionFactory */
    @Bean(name = "sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("dataSource") DataSource dataSource) throws Exception {
        return buildFactory(dataSource);
    }

    /** auth mapper 的 SqlSessionFactory：只挂 auth 数据源 */
    @Bean(name = "authSqlSessionFactory")
    public SqlSessionFactory authSqlSessionFactory(
            @Qualifier("authDataSource") DataSource authDataSource) throws Exception {
        return buildFactory(authDataSource);
    }

    private static SqlSessionFactory buildFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        factory.setPlugins(interceptor);
        // XML 目录（缺目录返回空集合，不报错）；两个工厂各自解析，互不干扰
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml"));
        return factory.getObject();
    }

    /**
     * 启动自检（双数据源最容易"看起来配好了、实际连错库"）：把两条数据源与**每个**
     * SqlSessionFactory 实际绑定的库打出来 —— 排查 order/analytics/user 三组 mapper 的归属时，
     * 这一行日志比看配置文件快得多。
     */
    @Bean
    public ApplicationRunner dataSourceSelfCheck(ApplicationContext context,
                                                 @Qualifier("dataSource") DataSource primary,
                                                 @Qualifier("authDataSource") DataSource auth) {
        return args -> {
            log.info("数据源自检: primary={}, auth={}", urlOf(primary), urlOf(auth));
            Map<String, SqlSessionFactory> factories = context.getBeansOfType(SqlSessionFactory.class);
            factories.forEach((name, factory) -> log.info("数据源自检: SqlSessionFactory[{}] -> {}",
                    name, urlOf(factory.getConfiguration().getEnvironment().getDataSource())));
        };
    }

    private static String urlOf(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari.getJdbcUrl();
        }
        return String.valueOf(dataSource);
    }
}
