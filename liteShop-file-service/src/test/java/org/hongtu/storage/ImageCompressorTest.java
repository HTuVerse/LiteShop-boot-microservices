package org.hongtu.storage;

import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 验证压缩开关、目标大小及图片格式和 GIF 帧保留。 */
class ImageCompressorTest {
    private final ImageCompressor compressor = new ImageCompressor();

    @Test void leavesSmallOrDisabledImagesUnchanged() throws Exception {
        byte[] image = still("png", 200, 200, true);
        MockMultipartFile file = file("small.png", image);
        PlatformServiceRuntimeDTO settings = settings(false, "0.1");
        assertNull(compressor.compress(file, "png", settings));
        settings.setImageCompressionEnabled(true);
        settings.setImageCompressionTargetMb(BigDecimal.valueOf(2));
        assertNull(compressor.compress(file, "png", settings));
    }

    @Test void compressesJpegPngAndWebpWithinTarget() throws Exception {
        for (String format : new String[]{"jpeg", "png", "webp"}) {
            byte[] image = still(format, 900, 900, true);
            assertTrue(image.length > 1024 * 1024 / 10, format);
            byte[] result = compressor.compress(file("photo." + format, image), format, settings(true, "0.1"));
            assertNotNull(result, format);
            assertTrue(result.length <= 1024 * 1024 / 10, format);
            assertNotNull(ImageIO.read(new ByteArrayInputStream(result)), format);
        }
    }

    @Test void preservesAnimatedGifFrames() throws Exception {
        byte[] image = animatedGif();
        assertTrue(image.length > 1024 * 1024 / 10);
        byte[] result = compressor.compress(file("animation.gif", image), "gif", settings(true, "0.1"));
        assertNotNull(result);
        assertTrue(result.length <= 1024 * 1024 / 10);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(result))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input);
                assertEquals(2, reader.getNumImages(true));
            } finally { reader.dispose(); }
        }
    }

    private PlatformServiceRuntimeDTO settings(boolean enabled, String target) {
        PlatformServiceRuntimeDTO result = new PlatformServiceRuntimeDTO();
        result.setImageCompressionEnabled(enabled);
        result.setImageCompressionTargetMb(new BigDecimal(target));
        return result;
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "image/" + name.substring(name.lastIndexOf('.') + 1), bytes);
    }

    private byte[] still(String format, int width, int height, boolean noise) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                image.setRGB(x, y, (noise ? random.nextInt() : x + y) | 0xff000000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes));
        return bytes.toByteArray();
    }

    private byte[] animatedGif() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int frame = 0; frame < 2; frame++) {
                BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
                Random random = new Random(frame);
                for (int y = 0; y < 600; y++)
                    for (int x = 0; x < 600; x++) image.setRGB(x, y, random.nextInt());
                writer.writeToSequence(new IIOImage(image, null, null), null);
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
        return bytes.toByteArray();
    }
}
