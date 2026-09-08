package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.service.storage.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OSS 存储策略激活与「未配置密钥」兜底集成测试
 *
 * <p>本类显式切 {@code storage.type=oss}（与默认 local 分属不同上下文）：
 * <ol>
 *   <li>验证仅 OSS 实现被 {@code @ConditionalOnProperty} 激活（唯一 bean，type=oss）；</li>
 *   <li>验证未配置真实 AccessKey 时 store 抛出带可读提示的业务错误且不发网络请求。</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "flash.stream.auto-poll=false",
        "aliyun.oss.enabled=true",
        // 显式置空覆盖环境变量 OSS_ACCESS_KEY_ID/SECRET（本机若已配置真实密钥，
        // yaml 的 ${OSS_ACCESS_KEY_ID:} 会被展开导致测试真的外呼 OSS），确保"未配置"场景可控
        "aliyun.oss.access-key-id=",
        "aliyun.oss.access-key-secret=",
})
public class OssStoragePolicyIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void ossBeanIsTheOnlyActiveStorageWhenOssEnabled() {
        Map<String, ImageStorageService> beans = context.getBeansOfType(ImageStorageService.class);
        // enabled=true 时 local 实现条件不满足，不应同时存在两个存储 bean
        assertThat(beans).hasSize(1);
        ImageStorageService storage = beans.values().iterator().next();
        assertThat(storage.type()).isEqualTo("oss");
    }

    @Test
    void storeWithoutAccessKeyFailsGracefullyWithReadableMessage() {
        ImageStorageService storage = context.getBean(ImageStorageService.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.SYSTEM_ERROR.getCode());
                    assertThat(be.getMessage())
                            .contains("OSS 未配置 AccessKey")
                            .contains("aliyun.oss.access-key-id");
                });
    }
}
