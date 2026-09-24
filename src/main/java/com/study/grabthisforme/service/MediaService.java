package com.study.grabthisforme.service;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.MediaEntity;
import com.study.grabthisforme.persistence.repository.MediaRepository;
import com.study.grabthisforme.persistence.repository.ConversationParticipantRepository;
import java.nio.file.*;
import java.io.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
@Service
public class MediaService {
    public static final long MAX_BYTES=8L*1024*1024;
    private final com.study.grabthisforme.persistence.repository.UserAccountRepository accounts;
    private final Path storage;
    private final MediaRepository media;
    private final ConversationParticipantRepository participants;
    private final com.study.grabthisforme.persistence.repository.ConversationRepository conversations;
    public MediaService(MediaRepository media, ConversationParticipantRepository participants,
        com.study.grabthisforme.persistence.repository.UserAccountRepository accounts,
        com.study.grabthisforme.persistence.repository.ConversationRepository conversations,
        @Value("${grabthisforme.media.directory:./data/media}") String directory) {
        this.conversations=conversations;
        this.accounts=accounts; this.media=media; this.participants=participants; this.storage=Path.of(directory).toAbsolutePath().normalize();
    }
    public record UploadedMedia(String url, String mediaId) {}
    @org.springframework.transaction.annotation.Transactional
    public UploadedMedia upload(long userId, MultipartFile file, String conversationId) throws IOException {
        if (file.isEmpty() || file.getSize()>MAX_BYTES) throw invalid("图片不能为空或超过 8 MB");
        if (conversationId!=null) lockConversation(conversationId,userId);
        accounts.findForUpdate(userId).orElseThrow(() -> invalid("Account not found"));
        final ImageEncoder.Encoded processed;
        try (var input = file.getInputStream()) {
            processed = ImageEncoder.encode(input, MAX_BYTES);
        } catch (IOException | IllegalArgumentException ex) {
            throw invalid(ex.getMessage() == null ? "图片数据无效" : ex.getMessage());
        }
        byte[] encoded = processed.bytes();
        String id;
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            digest.update((userId+":"+conversationId+":").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            id=java.util.HexFormat.of().formatHex(digest.digest(encoded));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        var existing=media.findById(id);
        if (existing.isPresent()) ensureNotRetiring(existing.get());
        if (existing.isPresent() && Files.isRegularFile(file(existing.get()))) {
            touchRetention(existing.get());
            return uploaded(existing.get());
        }
        if (existing.isEmpty() && media.countByOwnerIdAndCreatedTimeGreaterThan(userId,System.currentTimeMillis()-86400000)>99)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,42901,"Daily image upload limit reached");
        MediaEntity asset=new MediaEntity(); asset.mediaId=id; asset.ownerId=userId;
        asset.conversationId=conversationId; asset.createdTime=System.currentTimeMillis(); asset.byteSize=(long)encoded.length; asset.contentType=processed.contentType();
        Files.createDirectories(storage);
        Path target=file(asset);
        boolean created=false;
        try { Files.write(target,encoded,StandardOpenOption.CREATE_NEW); created=true; }
        catch (FileAlreadyExistsException ignored) { /* Retry after a database rollback can reuse identical content. */ }
        try { media.saveAndFlush(asset); } catch(RuntimeException ex) { if(created) Files.deleteIfExists(target); throw ex; }
        return uploaded(asset);
    }
    public static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;

    @org.springframework.transaction.annotation.Transactional
    public UploadedMedia uploadVideo(long userId, MultipartFile source, String conversationId) throws IOException {
        return uploadVideo(userId, source, conversationId, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public UploadedMedia uploadVideo(long userId, MultipartFile source, String conversationId, MultipartFile cover) throws IOException {
        if (source.isEmpty() || source.getSize() > MAX_VIDEO_BYTES)
            throw invalid("视频不能为空或超过 100 MB");
        if (conversationId != null) lockConversation(conversationId, userId);
        accounts.findForUpdate(userId).orElseThrow(() -> invalid("Account not found"));
        Files.createDirectories(storage);
        byte[] coverBytes = null;
        if (cover != null && !cover.isEmpty()) {
            if (cover.getSize() > 1024 * 1024) throw invalid("视频封面不能超过 1 MB");
            try (var input = cover.getInputStream()) {
                var encoded = ImageEncoder.encode(input, 1024 * 1024);
                if (!"image/jpeg".equals(encoded.contentType())) throw invalid("视频封面必须为 JPEG");
                coverBytes = encoded.bytes();
            } catch (IOException ex) { throw invalid("视频封面无效，请重新选择视频"); }
        }
        Path temporary = Files.createTempFile(storage, "video-", ".upload");
        try {
            java.security.MessageDigest digest;
            try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            digest.update((userId + ":" + conversationId + ":video:").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long bytes = 0;
            try (var input = source.getInputStream(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes += count;
                    if (bytes > MAX_VIDEO_BYTES) throw invalid("视频不能超过 100 MB");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            try { Mp4Validator.validate(temporary); }
            catch (IOException e) { throw invalid("视频格式无效：" + e.getMessage()); }
            String id = java.util.HexFormat.of().formatHex(digest.digest());
            var existing = media.findById(id);
            if (existing.isPresent()) ensureNotRetiring(existing.get());
            if (existing.isPresent() && Files.isRegularFile(file(existing.get()))) {
                touchRetention(existing.get());
                saveCover(existing.get(), coverBytes);
                return uploaded(existing.get());
            }
            if (existing.isEmpty() && media.countByOwnerIdAndCreatedTimeGreaterThan(userId, System.currentTimeMillis()-86400000) >= 100)
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, 42901, "今日媒体上传次数已达上限");
            MediaEntity asset = new MediaEntity();
            asset.mediaId = id; asset.ownerId = userId; asset.conversationId = conversationId;
            asset.createdTime = System.currentTimeMillis(); asset.byteSize = bytes; asset.contentType = "video/mp4";
            Path target = file(asset);
            boolean created = false;
            try { Files.move(temporary, target); created = true; }
            catch (FileAlreadyExistsException ignored) { }
            try {
                media.saveAndFlush(asset);
                saveCover(asset, coverBytes);
            } catch (RuntimeException | IOException e) {
                if (created) Files.deleteIfExists(target);
                throw e;
            }
            return uploaded(asset);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path coverFile(String id, Long userId) {
        var asset = accessible(id, userId);
        if (!"video/mp4".equals(asset.contentType)) throw invalid("附件不是视频");
        Path cover = storage.resolve(asset.mediaId + ".cover.jpg");
        if (!Files.isRegularFile(cover))
            throw new ApiException(HttpStatus.NOT_FOUND, 40472, "该视频暂无封面");
        return cover;
    }

    private void saveCover(MediaEntity asset, byte[] bytes) throws IOException {
        if (bytes == null) return;
        Path target = storage.resolve(asset.mediaId + ".cover.jpg");
        if (Files.isRegularFile(target)) return;
        Path temp = Files.createTempFile(storage, "cover-", ".upload");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, target); }
            catch (FileAlreadyExistsException ignored) { }
        } finally { Files.deleteIfExists(temp); }
    }

    public String validatePublicVideo(long userId, String url) {
        if (url == null || url.isBlank()) return null;
        String path;
        try { path = java.net.URI.create(url).getPath(); }
        catch (IllegalArgumentException e) { throw invalid("无效的视频地址"); }
        if (path == null || !path.matches("/?api/public/media/[a-zA-Z0-9-]+"))
            throw invalid("请先上传社区视频");
        String id = path.substring(path.lastIndexOf('/') + 1);
        var asset = accessible(id, userId);
        if (asset.conversationId != null || !java.util.Objects.equals(asset.ownerId, userId))
            throw new ApiException(HttpStatus.FORBIDDEN, 40372, "不能发布他人或私聊的视频");
        if (!"video/mp4".equals(asset.contentType)) throw invalid("附件不是视频");
        return "api/public/media/" + id;
    }

    private UploadedMedia uploaded(MediaEntity asset) {
        return new UploadedMedia((asset.conversationId==null?"api/public/media/":"api/media/")+asset.mediaId,asset.mediaId);
    }

    /** Return a canonical local path so a caller cannot substitute a tracking host. */
    public String validateChatAttachment(long userId,String conversationId,String url) {
        return validateChatAttachment(userId, conversationId, url, false);
    }
    public String validateChatAttachment(long userId,String conversationId,String url, boolean video) {
        if (url==null || url.isBlank()) throw invalid("Image attachment is required");
        String path;
        try {
            var uri=java.net.URI.create(url);
            if (uri.getScheme()!=null && !java.util.Set.of("http","https").contains(uri.getScheme()))
                throw invalid("Upload the image before sending it");
            path=uri.getPath();
        } catch (IllegalArgumentException ex) { throw invalid("Invalid attachment URL"); }
        if (path==null || !path.matches("/?api/media/[a-zA-Z0-9-]+")) throw invalid("Private image upload required");
        String id=path.substring(path.lastIndexOf('/')+1);
        var asset=accessible(id,userId);
        if (!java.util.Objects.equals(asset.conversationId,conversationId) || !java.util.Objects.equals(asset.ownerId,userId))
            throw new ApiException(HttpStatus.FORBIDDEN,40372,"Attachment does not belong to this sender and conversation");
        if (!(video ? "video/mp4".equals(asset.contentType) : java.util.Set.of("image/png", "image/jpeg").contains(asset.contentType))) throw invalid("附件类型与消息类型不一致");
        touchRetention(asset);
        return "api/media/"+id;
    }
    public MediaEntity accessible(String id, Long userId) {
        var asset=media.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,40471,"Image not found"));
        if (asset.conversationId!=null) {
            if (userId==null) throw new ApiException(HttpStatus.UNAUTHORIZED,40101,"Login required");
            checkParticipant(asset.conversationId,userId);
        }
        ensureNotRetiring(asset);
        return asset;
    }
    public Path file(MediaEntity asset) {
        String extension = switch (asset.contentType) {
            case "video/mp4" -> ".mp4";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw invalid("不支持的媒体类型");
        };
        return storage.resolve(asset.mediaId + extension);
    }

    /** Forward only an attachment of a checked source message, into a new conversation-scoped asset. */
    public String copyForForward(long uid, String sourceConversation, String targetConversation, String url) {
        checkParticipant(sourceConversation, uid);
        checkParticipant(targetConversation, uid);
        String path;
        try { path = java.net.URI.create(url).getPath(); }
        catch (IllegalArgumentException ex) { throw invalid("原附件地址无效"); }
        if (path == null || !path.matches("/?api/media/[a-zA-Z0-9-]+")) throw invalid("原附件不可转发");
        var source = accessible(path.substring(path.lastIndexOf('/') + 1), uid);
        if (!sourceConversation.equals(source.conversationId)) throw invalid("原附件不属于该会话");
        if (!Files.isRegularFile(file(source))) throw invalid("原附件已过期");
        accounts.findForUpdate(uid).orElseThrow();
        if (media.countByOwnerIdAndCreatedTimeGreaterThan(uid, System.currentTimeMillis()-86400000) >= 100)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, 42901, "今日媒体处理次数已达上限");
        var copy = new MediaEntity();
        copy.mediaId = UUID.randomUUID().toString(); copy.ownerId = uid; copy.conversationId = targetConversation;
        copy.contentType = source.contentType; copy.byteSize = source.byteSize;
        copy.createdTime = System.currentTimeMillis(); copy.retentionTime = copy.createdTime;
        Path target = file(copy);
        Path targetCover = storage.resolve(copy.mediaId + ".cover.jpg");
        try {
            Files.copy(file(source), target);
            Path sourceCover = storage.resolve(source.mediaId + ".cover.jpg");
            if (Files.isRegularFile(sourceCover)) Files.copy(sourceCover, targetCover);
            media.saveAndFlush(copy);
        } catch (IOException | RuntimeException ex) {
            try { Files.deleteIfExists(target); Files.deleteIfExists(targetCover); } catch (IOException ignored) {}
            throw invalid("附件转发失败，请稍后重试");
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { Files.deleteIfExists(target); Files.deleteIfExists(targetCover); } catch (IOException ignored) {}
                    }
                }
            });
        return uploaded(copy).url();
    }
    private void lockConversation(String id, long userId) {
        checkParticipant(id, userId);
        conversations.findForUpdate(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, 40461, "会话不存在"));
    }

    private void ensureNotRetiring(MediaEntity asset) {
        if (Boolean.TRUE.equals(asset.deletionPending))
            throw new ApiException(HttpStatus.GONE, 41071, "聊天附件已过期，请稍后重新上传");
    }

    private void touchRetention(MediaEntity asset) {
        if (asset.conversationId != null) {
            asset.retentionTime = System.currentTimeMillis();
            media.save(asset);
        }
    }

    /** Only called for a previously committed tombstone. Never recursively delete or scan storage. */
    void deleteRetiredFiles(MediaEntity asset) throws IOException {
        if (asset.conversationId == null || !Boolean.TRUE.equals(asset.deletionPending) ||
            asset.mediaId == null || !asset.mediaId.matches("[a-zA-Z0-9-]+"))
            throw new IOException("Invalid retention target");
        Path main = file(asset).toAbsolutePath().normalize();
        Path cover = storage.resolve(asset.mediaId + ".cover.jpg").toAbsolutePath().normalize();
        if (!storage.equals(main.getParent()) || !storage.equals(cover.getParent()))
            throw new IOException("Retention target is outside media storage");
        Files.deleteIfExists(main);
        Files.deleteIfExists(cover);
    }

    private void checkParticipant(String id,long userId) {
        if (participants.findAllByConversationIdOrderBySortOrderAsc(id).stream().noneMatch(p->p.userId.equals(userId)))
            throw new ApiException(HttpStatus.FORBIDDEN,40371,"Not a conversation participant");
    }
    private ApiException invalid(String text) { return new ApiException(HttpStatus.BAD_REQUEST,40071,text); }
}
