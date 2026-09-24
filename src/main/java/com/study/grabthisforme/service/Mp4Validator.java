package com.study.grabthisforme.service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Set;

/** Bounded ISO-BMFF container validation, not a codec/transcoding service. */
final class Mp4Validator {
    private static final Set<String> BRANDS = Set.of("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ");

    private static String fourcc(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    static void validate(Path path) throws IOException {
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length(), offset = 0;
            boolean ftyp = false, moov = false, mdat = false;
            int boxes = 0;
            while (offset < length) {
                if (++boxes > 100000 || length - offset < 8) throw new IOException("无效的 MP4 文件结构");
                file.seek(offset);
                long size = Integer.toUnsignedLong(file.readInt());
                String type = fourcc(file);
                long header = 8;
                if (size == 1) { size = file.readLong(); header = 16; }
                else if (size == 0) size = length - offset;
                if (size < header || size > length - offset) throw new IOException("MP4 文件不完整");
                if (offset == 0 && !"ftyp".equals(type)) throw new IOException("仅支持 MP4 视频");
                if ("ftyp".equals(type)) {
                    if (ftyp || size < header + 8 || size > 4096) throw new IOException("无效的 MP4 类型");
                    boolean compatible = BRANDS.contains(fourcc(file));
                    file.readInt();
                    for (long pos = header + 8; pos + 4 <= size; pos += 4) compatible |= BRANDS.contains(fourcc(file));
                    if (!compatible) throw new IOException("不支持的视频容器，请转换为 MP4");
                    ftyp = true;
                }
                if ("moov".equals(type)) moov = size > header;
                if ("mdat".equals(type)) mdat = size > header;
                offset += size;
            }
            if (!ftyp || !moov || !mdat) throw new IOException("MP4 缺少视频数据或索引");
        }
    }
}
