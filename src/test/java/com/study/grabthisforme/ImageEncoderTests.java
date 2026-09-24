package com.study.grabthisforme;

import com.study.grabthisforme.service.ImageEncoder;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImageEncoderTests {
    byte[] encode(BufferedImage image, String format) throws Exception {
        var out = new ByteArrayOutputStream(); ImageIO.write(image, format, out); return out.toByteArray();
    }
    @Test void jpegPhotoStaysJpegAndIsSmallerThanPng() throws Exception {
        var image = new BufferedImage(2048, 1536, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(42);
        for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++)
            image.setRGB(x,y,random.nextInt(0x1000000));
        byte[] jpeg = encode(image, "jpeg");
        var processed = ImageEncoder.encode(new ByteArrayInputStream(jpeg), 8*1024*1024);
        assertThat(processed.contentType()).isEqualTo("image/jpeg");
        assertThat(processed.bytes().length).isLessThan(encode(image, "png").length);
        assertThat(processed.bytes().length).isLessThanOrEqualTo(jpeg.length);
        var decoded = ImageIO.read(new ByteArrayInputStream(processed.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(2048);
        assertThat(decoded.getHeight()).isEqualTo(1536);
        System.out.printf("image_fixture dimensions=2048x1536 before_bytes=%d after_bytes=%d%n", jpeg.length, processed.bytes().length);
    }
    @Test void alphaSurvivesAndTinyPngDoesNotBecomeLargerJpeg() throws Exception {
        var alpha = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        alpha.setRGB(3, 4, 0x80112233);
        var processed = ImageEncoder.encode(new ByteArrayInputStream(encode(alpha, "png")), 8*1024*1024);
        assertThat(processed.contentType()).isEqualTo("image/png");
        assertThat(ImageIO.read(new ByteArrayInputStream(processed.bytes())).getRGB(3,4)).isEqualTo(0x80112233);
        var tiny = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        assertThat(ImageEncoder.encode(new ByteArrayInputStream(encode(tiny, "png")), 8*1024*1024).contentType()).isEqualTo("image/png");
    }
    @Test void limitsAndForgedFormatsAreIndependentOfClient() {
        assertThatThrownBy(() -> ImageEncoder.encode(new ByteArrayInputStream(new byte[1025]), 1024)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImageEncoder.encode(new ByteArrayInputStream("GIF89a".getBytes()), 1024)).isInstanceOf(IOException.class);
    }
    @Test void metadataAndTrailingBytesAreNotStored() throws Exception {
        byte[] jpeg = encode(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "jpeg");
        var input = new ByteArrayOutputStream();
        input.write(jpeg,0,2);
        input.write(new byte[]{(byte)255,(byte)254,0,12});
        input.write("SECRET_GPS".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        input.write(jpeg,2,jpeg.length-2);
        input.write("TRAILING_PRIVATE".getBytes());
        var result = ImageEncoder.encode(new ByteArrayInputStream(input.toByteArray()), 8*1024*1024);
        String bytes = new String(result.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(bytes).doesNotContain("SECRET_GPS", "TRAILING_PRIVATE");
    }
}
