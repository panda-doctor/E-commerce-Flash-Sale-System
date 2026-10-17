package com.ghb.ecommerceflashsalesystem.guard;

import com.ghb.ecommerceflashsalesystem.ECommerceFlashSaleSystemApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4 收官（Day 10）的**不变量守卫**——纯 JUnit，不启 Spring 上下文（秒级、无外部依赖）。
 *
 * <p>三阶段迁移（单体 → 域服务 → 旧库下线）完成后，有几条"从此刻起必须永远成立"的约束。
 * 它们此前只写在文档与注释里；本类把它们变成**会失败的断言**，防止后人无意间回潮：</p>
 * <ol>
 *   <li><b>单体不再扫描任何 domain mapper</b> —— 否则又会需要旧库/域库的表；</li>
 *   <li><b>单体装配面不含 product/activity/order/seckill</b> —— 那批 Bean 是"永远不被调用的死链"
 *       （旧库下线前它们的唯一存在理由是给 mapper 当宿主）；</li>
 *   <li><b>单体不连库</b> —— 数据源自动配置以**属性方式**排除（属性可被测试 properties 置空覆盖；
 *       类级 {@code exclude=…} 则不可，这正是 Day 10 首次尝试红掉的原因，故连"类级 exclude"也不许出现）；</li>
 *   <li><b>反向镜像默认关闭</b> —— 旧库已 DROP，任何"默认开"的镜像都会写向不存在的库。</li>
 * </ol>
 */
class PostMigrationInvariantsTest {

    static final String APP_CLASS = "ECommerceFlashSaleSystemApplication";

    /** 1. 单体启动类上**不得**再有 @MapperScan（旧库/域库的最后一条装配级依赖）。 */
    @Test
    @DisplayName("单体不再有 @MapperScan")
    void monolithHasNoMapperScan() {
        assertThat(ECommerceFlashSaleSystemApplication.class.getAnnotation(MapperScan.class))
                .as("%s 不得再声明 @MapperScan：单体生产侧已无任何 mapper 需求"
                        + "（快照写走 ObjectProvider、订单读走 order 服务、会话校验走 auth 服务）", APP_CLASS)
                .isNull();
    }

    /** 2. 装配面不得包含 domain 包（product/activity/order/seckill）。 */
    @Test
    @DisplayName("单体装配面不含 domain 包")
    void monolithScanExcludesDomainPackages() {
        SpringBootApplication anno = ECommerceFlashSaleSystemApplication.class
                .getAnnotation(SpringBootApplication.class);
        assertThat(anno).as("@SpringBootApplication 必须可读").isNotNull();
        String joined = String.join(",", anno.scanBasePackages());
        assertThat(joined).as("scanBasePackages 不得包含 product").doesNotContain("product");
        assertThat(joined).as("scanBasePackages 不得包含 activity").doesNotContain("activity");
        assertThat(joined).as("scanBasePackages 不得包含 order").doesNotContain("order");
        assertThat(joined).as("scanBasePackages 不得包含 seckill").doesNotContain("seckill");
    }

    /**
     * 3. 数据源必须用**属性**排除，不得用类级 exclude。
     *
     * <p>类级 {@code @SpringBootApplication(exclude=…)} 编译进 classpath，测试无法撤销 ——
     * Day 10 首次尝试正是因此在 bootstrap 集成测试上红掉（测试需要数据源跑域 Bean）。
     * 属性方式（{@code spring.autoconfigure.exclude}）可被测试 properties 置空覆盖。</p>
     */
    @Test
    @DisplayName("数据源排除走属性，不走类级 exclude")
    void dataSourceExcludedByProperty() throws Exception {
        SpringBootApplication anno = ECommerceFlashSaleSystemApplication.class
                .getAnnotation(SpringBootApplication.class);
        assertThat(anno).isNotNull();
        assertThat(anno.exclude())
                .as("不得用类级 exclude（测试无法撤销）；请用 spring.autoconfigure.exclude")
                .isEmpty();

        try (InputStream in = getClass().getResourceAsStream("/application.yaml")) {
            assertThat(in).as("bootstrap 主配置必须在 classpath 上").isNotNull();
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(yaml)
                    .as("主配置必须以属性方式排除 DataSourceAutoConfiguration（单体不连库）")
                    .contains("DataSourceAutoConfiguration");
        }
    }

    /** 4. 反向镜像实现类必须已被删除（旧库已 DROP，镜像无处可写）。 */
    @Test
    @DisplayName("镜像实现类已删除")
    void mirrorClassesRemoved() {
        for (String fqcn : new String[]{
                "com.ghb.ecommerceflashsalesystem.infra.db.DualWriteMirror",
                "com.ghb.ecommerceflashsalesystem.infra.db.DualWriteProperties",
                "com.ghb.ecommerceflashsalesystem.order.mirror.OrderMirror",
                "com.ghb.ecommerceflashsalesystem.user.mirror.AuthMirror"}) {
            assertThatThrownBy(() -> Class.forName(fqcn))
                    .as("%s 应已随旧库下线删除（P4 收官）；若需回滚请从 git 历史恢复", fqcn)
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }
}
