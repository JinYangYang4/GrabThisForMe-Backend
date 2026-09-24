package com.study.grabthisforme.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import javax.imageio.*;

/** Decode independently of the supplied MIME/extension. Never trust client-side validation. */
public final class ImageEncoder {
    private ImageEncoder() {}
    public record Encoded(byte[] bytes, String contentType) {}

    public static Encoded encode(InputStream stream, long maxBytes) throws IOException {
        byte[] original = stream.readNBytes(Math.toIntExact(maxBytes + 1));
        if (original.length == 0 || original.length > maxBytes) throw new IOException("图片不能为空或超过 8 MB");
        rejectAnimatedPng(original);
        BufferedImage bitmap;
        String sourceFormat;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("不支持的图片格式，请上传静态 JPEG 或 PNG");
            var reader = readers.next();
            try {
                sourceFormat = reader.getFormatName();
                if (!sourceFormat.equalsIgnoreCase("JPEG") && !sourceFormat.equalsIgnoreCase("PNG"))
                    throw new IOException("不支持此图片格式或动画，请上传静态 JPEG 或 PNG");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 20_000_000)
                    throw new IOException("图片不能超过 2000 万像素（20 megapixels）");
                bitmap = reader.read(0);
            } finally { reader.dispose(); }
        }
        try {
            boolean alpha = hasTransparency(bitmap);
            // Re-encode without input metadata, keeping PNG for transparency or a genuinely smaller PNG.
            byte[] encoded;
            String type;
            if (alpha) { encoded = write(bitmap, "png", 1f); type = "image/png"; }
            else {
                encoded = write(bitmap, "jpeg", .85f); type = "image/jpeg";
                // Do not increase a low-quality JPEG just by encoding it at a higher quality.
                if (sourceFormat.equalsIgnoreCase("JPEG")) {
                    for (float quality : new float[]{.75f, .65f}) {
                        if (encoded.length <= original.length) break;
                        byte[] candidate = write(bitmap, "jpeg", quality);
                        if (candidate.length < encoded.length) encoded = candidate;
                    }
                }
                if (sourceFormat.equalsIgnoreCase("PNG")) {
                    byte[] png = write(bitmap, "png", 1f);
                    if (png.length < encoded.length) { encoded = png; type = "image/png"; }
                }
            }
            if (encoded.length > maxBytes) throw new IOException("图片处理后仍超过 8 MB，请降低图片尺寸");
            return new Encoded(encoded, type);
        } finally { bitmap.flush(); }
    }

    private static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return false;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) >>> 24) != 255) return true;
        return false;
    }

    private static byte[] write(BufferedImage image, String format, float quality) throws IOException {
        BufferedImage rgb = image;
        if (format.equals("jpeg") && image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = rgb.createGraphics();
            try { graphics.drawImage(image, 0, 0, null); } finally { graphics.dispose(); }
        }
        var writer = ImageIO.getImageWritersByFormatName(format).next();
        try (var bytes = new ByteArrayOutputStream(); var output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            var params = writer.getDefaultWriteParam();
            if (format.equals("jpeg")) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(rgb, null, null), params);
            output.flush();
            return bytes.toByteArray();
        } finally { writer.dispose(); if (rgb != image) rgb.flush(); }
    }

    private static void rejectAnimatedPng(byte[] bytes) throws IOException {
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < 8 || !java.util.Arrays.equals(signature, java.util.Arrays.copyOf(bytes, 8))) return;
        int position = 8;
        while (position + 12 <= bytes.length) {
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, position, 4).getInt());
            if (size > bytes.length - position - 12L) throw new IOException("PNG 图片已损坏");
            String chunk = new String(bytes, position + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (chunk.equals("acTL")) throw new IOException("暂不支持动画图片");
            position += (int)size + 12;
            if (chunk.equals("IEND")) return;
        }
        throw new IOException("PNG 图片不完整");
    }
}
