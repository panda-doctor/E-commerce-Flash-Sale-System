package com.ghb.ecommerceflashsalesystem.unit;

import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.service.storage.AbstractImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageStorageValidationTest {

    private final TestImageStorage storage = new TestImageStorage();

    @Test
    void acceptsPngWithMatchingFileSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "text/plain",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        assertThat(storage.inspect(file)).isEqualTo("image/png");
    }

    @Test
    void rejectsTextDisguisedAsPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> storage.inspect(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件内容与图片格式不匹配");
    }

    private static final class TestImageStorage extends AbstractImageStorage {

        private String inspect(MultipartFile file) {
            validate(file);
            return mediaTypeOf(file.getOriginalFilename());
        }

        @Override
        public String store(MultipartFile file) {
            return inspect(file);
        }

        @Override
        public String type() {
            return "test";
        }
    }
}
