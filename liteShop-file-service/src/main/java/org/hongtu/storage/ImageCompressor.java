package org.hongtu.storage;

import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Iterator;

/** 按平台目标大小压缩图片，保留格式、透明通道及 GIF 动画。 */
@Component
public class ImageCompressor {
    private static final long MAX_PIXELS = 50_000_000;
    private static final int MAX_GIF_FRAMES = 200;
    private static final double[] SCALES = {1, .85, .7, .55, .4, .3, .2, .15, .1};
    private static final float[] QUALITIES = {.85f, .72f, .58f, .45f};

    /** 未启用或已满足目标时返回 null，调用方直接写入原文件。 */
    public byte[] compress(MultipartFile file, String extension, PlatformServiceRuntimeDTO settings) throws Exception {
        if (!Boolean.TRUE.equals(settings.getImageCompressionEnabled())) return null;
        BigDecimal targetMb = settings.getImageCompressionTargetMb();
        if (targetMb == null || targetMb.signum() <= 0)
            throw new BusinessException(400, "图片压缩目标未配置");
        long limit = targetMb.multiply(BigDecimal.valueOf(1024L * 1024L)).longValue();
        if (file.getSize() <= limit) return null;

        String format = "jpg".equals(extension) ? "jpeg" : extension;
        // 当前 WebP 编码器只写静态图，超限动图明确拒绝，避免上传后丢失动画。
        if ("webp".equals(format) && animatedWebp(file))
            throw new BusinessException(400, "该动态 WebP 超过压缩目标，无法在保留动画时压缩");
        try (InputStream source = file.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(source)) {
            if (input == null) throw new BusinessException(400, "无法读取图片");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(400, "当前服务无法压缩该图片格式");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
                    throw new BusinessException(400, "图片像素过大，无法安全压缩");
                if ("gif".equals(format)) return compressGif(reader, limit);
                BufferedImage image = reader.read(0);
                for (double scale : SCALES) {
                    BufferedImage resized = resize(image, scale, "jpeg".equals(format));
                    for (float quality : "jpeg".equals(format) || "webp".equals(format) ? QUALITIES : new float[]{1f}) {
                        byte[] encoded = encode(resized, format, quality);
                        if (encoded.length <= limit) return encoded;
                    }
                }
            } finally { reader.dispose(); }
        } catch (BusinessException error) {
            throw error;
        } catch (Exception | LinkageError error) {
            throw new BusinessException(400, "图片压缩失败，请更换图片或提高压缩目标");
        }
        throw new BusinessException(400, "图片无法压缩到设定大小，请更换图片或提高压缩目标");
    }

    private byte[] compressGif(ImageReader reader, long limit) throws Exception {
        int frames = reader.getNumImages(true);
        if (frames > MAX_GIF_FRAMES)
            throw new BusinessException(400, "GIF 帧数过多，无法安全压缩");
        for (double scale : SCALES) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageWriter writer = writer("gif");
            try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(output);
                IIOMetadata sourceStream = reader.getStreamMetadata();
                IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(null);
                if (sourceStream != null && streamMetadata != null) {
                    Node tree = sourceStream.getAsTree("javax_imageio_gif_stream_1.0");
                    Node screen = child(tree, "LogicalScreenDescriptor");
                    scale(screen, "logicalScreenWidth", scale);
                    scale(screen, "logicalScreenHeight", scale);
                    streamMetadata.setFromTree("javax_imageio_gif_stream_1.0", tree);
                }
                writer.prepareWriteSequence(streamMetadata);
                for (int frame = 0; frame < frames; frame++) {
                    BufferedImage resized = resize(reader.read(frame), scale, false);
                    IIOMetadata sourceMetadata = reader.getImageMetadata(frame);
                    IIOMetadata metadata = writer.getDefaultImageMetadata(
                            ImageTypeSpecifier.createFromRenderedImage(resized), null);
                    Node tree = sourceMetadata.getAsTree("javax_imageio_gif_image_1.0");
                    if (scale != 1) {
                        Node descriptor = child(tree, "ImageDescriptor");
                        scale(descriptor, "imageLeftPosition", scale);
                        scale(descriptor, "imageTopPosition", scale);
                        set(descriptor, "imageWidth", resized.getWidth());
                        set(descriptor, "imageHeight", resized.getHeight());
                    }
                    metadata.setFromTree("javax_imageio_gif_image_1.0", tree);
                    writer.writeToSequence(new IIOImage(resized, null, metadata), null);
                }
                writer.endWriteSequence();
            } finally { writer.dispose(); }
            if (bytes.size() <= limit) return bytes.toByteArray();
        }
        throw new BusinessException(400, "GIF 无法在保留动画时压缩到设定大小");
    }

    private byte[] encode(BufferedImage image, String format, float quality) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = writer(format);
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (("jpeg".equals(format) || "webp".equals(format)) && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if ("webp".equals(format)) param.setCompressionType("Lossy");
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally { writer.dispose(); }
        return bytes.toByteArray();
    }

    private ImageWriter writer(String format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new BusinessException(400, "当前服务无法压缩该图片格式");
        return writers.next();
    }

    private BufferedImage resize(BufferedImage image, double scale, boolean opaque) {
        int width = dimension(image.getWidth(), scale), height = dimension(image.getHeight(), scale);
        BufferedImage result = new BufferedImage(width, height,
                opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return result;
    }

    private int dimension(int size, double scale) { return Math.max(1, (int) Math.round(size * scale)); }

    private Node child(Node parent, String name) {
        for (Node item = parent.getFirstChild(); item != null; item = item.getNextSibling())
            if (name.equals(item.getNodeName())) return item;
        throw new BusinessException(400, "GIF 元数据不完整");
    }

    private void set(Node node, String name, int value) {
        Node attribute = node.getAttributes().getNamedItem(name);
        if (attribute == null) throw new BusinessException(400, "GIF 元数据不完整");
        attribute.setNodeValue(Integer.toString(value));
    }

    private void scale(Node node, String name, double factor) {
        int value = Integer.parseInt(node.getAttributes().getNamedItem(name).getNodeValue());
        set(node, name, Math.max(0, (int) Math.round(value * factor)));
    }

    private boolean animatedWebp(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(21);
            return header.length == 21 && header[12] == 'V' && header[13] == 'P'
                    && header[14] == '8' && header[15] == 'X' && (header[20] & 0x02) != 0;
        }
    }
}
