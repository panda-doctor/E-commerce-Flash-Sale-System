package com.ghb.ecommerceflashsalesystem.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * flash-sale-ai 服务自身的架构约束。
 *
 * <p>本条规则（R9）原先在单体的 {@code ArchitectureTest} 中，P2 Day 3「ai 域服务化」时随类迁入本模块——
 * 单体已不再依赖 flash-sale-ai，规则留在单体里会因"选不到类"而失效（ArchUnit fail on empty should）。
 * 迁移原则：<b>架构规则应当与它约束的类放在同一个模块</b>。</p>
 *
 * <p>约束内容：AI 客服只读取商品与活动目录，不得触碰交易链路（seckill / order / analytics）与鉴权域
 * （user / auth-core）。</p>
 */
@AnalyzeClasses(
        packages = "com.ghb.ecommerceflashsalesystem",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AiArchitectureTest {

    @ArchTest
    static final ArchRule R9_ai只允许依赖商品与活动 = noClasses()
            .that().resideInAPackage("..ai..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..user..", "..auth..", "..seckill..", "..order..", "..analytics..")
            .because("AI 客服只读取商品与活动目录，不得触碰交易链路与鉴权域");
}
