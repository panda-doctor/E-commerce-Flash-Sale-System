package com.ghb.ecommerceflashsalesystem.unit;

import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.service.storage.AbstractImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图片上传校验单测（E6 魔数优先策略，2026-09-09）
 *
 * <p>策略要点：<b>文件头魔数是唯一权威</b>——内容必须是白名单图片（jpg/png/gif/bmp/webp），
 * 文件名扩展名不参与拦截；存储名与 Content-Type 均按探测出的真实格式生成。
 * 覆盖：各格式正常通过 / 扩展名与实际格式不符不再误拒 / 无扩展名可收 /
 * 伪装脚本与其它格式（PDF 等）被拒 / 超 5MB 与空文件被拒。
 */
class ImageStorageValidationTest {

    private final TestImageStorage storage = new TestImageStorage();

    // ---------- 各格式真实文件头 ----------

    private static byte[] pngHeader() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private static byte[] jpgHeader() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    }

    private static byte[] gifHeader() {
        return new byte[]{'G', 'I', 'F', '8', '9', 'a'};
    }

    private static byte[] bmpHeader() {
        return new byte[]{'B', 'M', 0x00, 0x00};
    }

    private static byte[] webpHeader() {
        return new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
    }

    @Test
    void acceptsPngWhenExtensionAndNameArePlausible() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", pngHeader());
        assertThat(storage.inspect(file)).isEqualTo("image/png");
    }

    @Test
    void acceptsAllSupportedImageFormats() {
        assertThat(storage.inspect(file("a.jpg", "image/jpeg", jpgHeader()))).isEqualTo("image/jpeg");
        assertThat(storage.inspect(file("a.gif", "image/gif", gifHeader()))).isEqualTo("image/gif");
        assertThat(storage.inspect(file("a.bmp", "image/bmp", bmpHeader()))).isEqualTo("image/bmp");
        assertThat(storage.inspect(file("a.webp", "image/webp", webpHeader()))).isEqualTo("image/webp");
    }

    @Test
    void acceptsRealImageWithMismatchedExtension() {
        // E6 误拒根因场景：微信/网页另存常出现「.png 实为 JPEG」等扩展名与内容不符，
        // 魔数优先策略下按真实格式放行并识别（存储名/Content-Type 会按 jpg 生成）
        MockMultipartFile file = new MockMultipartFile("file", "snapshot.png", "image/png", jpgHeader());
        assertThat(storage.inspect(file)).isEqualTo("image/jpeg");
    }

    @Test
    void acceptsImageWithoutExtensionInFilename() {
        // 无扩展名不拦截：内容校验通过即可（文件名本就不可信，存储名由服务端按真实格式生成）
        MockMultipartFile file = new MockMultipartFile("file", "wechat_image", "application/octet-stream", pngHeader());
        assertThat(storage.inspect(file)).isEqualTo("image/png");
    }

    @Test
    void rejectsScriptDisguisedAsImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> storage.inspect(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容不是受支持的图片格式");
    }

    @Test
    void rejectsPdfDisguisedAsJpg() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "%PDF-1.4 fake pdf content".getBytes());

        assertThatThrownBy(() -> storage.inspect(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容不是受支持的图片格式");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage.inspect(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件不能为空");
    }

    @Test
    void rejectsFileLargerThan5Mb() {
        // 与 AbstractImageStorage.MAX_IMAGE_SIZE（5MB）一致；超限在读取文件头之前拦截
        byte[] huge = new byte[5 * 1024 * 1024 + 1];
        byte[] png = pngHeader();
        System.arraycopy(png, 0, huge, 0, png.length);
        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", huge);

        assertThatThrownBy(() -> storage.inspect(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片大小不能超过 5MB");
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private static final class TestImageStorage extends AbstractImageStorage {

        /** 校验并返回按真实格式生成的 Content-Type（模拟 Local/OSS 落盘前的同一条校验链） */
        private String inspect(MultipartFile file) {
            String ext = validateAndDetectExt(file);
            return mediaTypeOf(ext);
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
