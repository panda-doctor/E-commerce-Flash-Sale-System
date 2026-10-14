package com.ghb.ecommerceflashsalesystem.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * 模块化单体架构约束（P0 Day 4 落地，12 条规则）。
 *
 * <p>与 {@code docs/microservices-plan.md} 9.3「模块依赖矩阵」一一对应：
 * 规则把"约定"变成"编译后可验证的红线"，后续拆服务时边界不会悄悄腐化。</p>
 *
 * <p>过渡豁免：装配层 {@code bootstrap} 的白名单组件（ApiAccessInterceptor / ApiAccessWebConfig /
 * AdminSeckillController / HealthController）允许被 bootstrap 自身持有，规则已按此设计（R5 只禁止
 * 业务模块反向依赖 bootstrap，不禁 bootstrap 内部使用）。</p>
 */
@AnalyzeClasses(
        packages = "com.ghb.ecommerceflashsalesystem",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ==================== R1 分层基础 ====================

    @ArchTest
    static final ArchRule R1_common必须零框架依赖 = noClasses()
            .that().resideInAPackage("..common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "com.baomidou..", "org.redisson..",
                    "jakarta.servlet..", "org.apache.ibatis..")
            .because("common 是所有模块的依赖底座，引入框架会导致版本与边界双重失控");

    @ArchTest
    static final ArchRule R2_契约与领域模型不得依赖业务域或infra = noClasses()
            .that().resideInAnyPackage("..contracts..", "..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infra..", "..user..", "..product..", "..activity..",
                    "..seckill..", "..order..", "..analytics..", "..ai..", "..bootstrap..")
            .because("契约/实体必须被业务域单向依赖，反向依赖会形成环");

    // ==================== R2/R3 分层调用方向 ====================

    @ArchTest
    static final ArchRule R3_service不得依赖controller = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule R4_controller不得直接依赖mapper = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..mapper..")
            .because("控制器必须经服务层访问数据（P0 已修复 OrderController / ProductController 两处直连）");

    @ArchTest
    static final ArchRule R5_业务模块不得反向依赖bootstrap = noClasses()
            .that().resideOutsideOfPackage("..bootstrap..")
            .should().dependOnClassesThat().resideInAPackage("..bootstrap..")
            .because("bootstrap 是装配层，被业务模块依赖会形成环（P0 已把鉴权常量上移 common）");

    // ==================== R4/R5 模块环与依赖矩阵 ====================

    @ArchTest
    static final ArchRule R6_模块之间不得存在循环依赖 =
            com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
            .matching("com.ghb.ecommerceflashsalesystem.(*)..")
            .should().beFreeOfCycles()
            .because("order→analytics 与 analytics 的订单读取已用 OrderQueryPort 解环");

    @ArchTest
    static final ArchRule R7_秒杀与订单只允许通过契约通信 = noClasses()
            .that().resideInAPackage("..seckill..")
            .should().dependOnClassesThat().resideInAPackage("..order..")
            .because("seckill ↔ order 之间只允许 contracts（消息契约）与 infra（共享发布器）");

    @ArchTest
    static final ArchRule R8_订单不得依赖秒杀 = noClasses()
            .that().resideInAPackage("..order..")
            .should().dependOnClassesThat().resideInAPackage("..seckill..");

    // R9（ai 只允许依赖商品与活动）已随 P2 Day 3「ai 域服务化」迁至 flash-sale-ai 模块自身的架构测试：
    // bootstrap 不再依赖 flash-sale-ai，其类不在本测试的分析范围内（规则见 flash-sale-ai 的 AiArchitectureTest）。

    @ArchTest
    static final ArchRule R10_订单不得依赖商品活动秒杀 = noClasses()
            .that().resideInAPackage("..order..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..product..", "..activity..", "..seckill..");

    // ==================== R6 命名与工程规范 ====================

    @ArchTest
    static final ArchRule R11a_控制器命名规范 = classes()
            .that().resideInAPackage("..controller..")
            .should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule R11b_数据访问命名规范 = classes()
            .that().resideInAPackage("..mapper..")
            .should().haveSimpleNameEndingWith("Mapper");

    /** 只约束顶层类：内嵌的记录/值对象（如 UserSessionService.AuthenticatedSession）不受命名规则限制 */
    private static final DescribedPredicate<JavaClass> TOP_LEVEL_CLASS =
            new DescribedPredicate<>("顶层类") {
                @Override
                public boolean test(JavaClass input) {
                    return !input.getName().contains("$");
                }
            };

    @ArchTest
    static final ArchRule R11c_服务层命名规范 = classes()
            .that(resideInAPackage("..service..").and(TOP_LEVEL_CLASS))
            .should().haveSimpleNameEndingWith("Service")
            .orShould().haveSimpleNameEndingWith("Impl")
            .because("service 包只放接口与实现（ai 的 LLM 客户端已迁至 ai.client）");

    @ArchTest
    static final ArchRule R12a_事务注解只允许声明在服务层 = methods()
            .that().areAnnotatedWith(Transactional.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule R12b_禁止字段注入 = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("统一构造器注入（Lombok @RequiredArgsConstructor），避免隐藏依赖");

    @ArchTest
    static final ArchRule R12c_禁止System_out = noClasses()
            .should().accessField(System.class, "out")
            .because("统一走 SLF4J 日志，便于 traceId 贯通与日志聚合");
}
