package com.study.grabthisforme.controller;
import com.study.grabthisforme.common.*;
import com.study.grabthisforme.service.MediaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
@RestController @RequestMapping("/api")
public class MediaController {
    private final MediaService media;
    public MediaController(MediaService media) { this.media=media; }
    @PostMapping(value="/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaService.UploadedMedia> upload(@RequestPart("file") MultipartFile file,
        @RequestParam(required=false) String conversationId) throws IOException {
        return ApiResponse.success(media.upload(AuthContext.requireUserId(),file,conversationId));
    }
    @PostMapping(value="/media/videos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaService.UploadedMedia> uploadVideo(@RequestPart("file") MultipartFile file,
        @RequestPart(value="cover", required=false) MultipartFile cover,
        @RequestParam(required=false) String conversationId) throws IOException {
        return ApiResponse.success(media.uploadVideo(AuthContext.requireUserId(), file, conversationId, cover));
    }
    @GetMapping("/media/{id}")
    public ResponseEntity<FileSystemResource> privateImage(@PathVariable String id) { return image(id,AuthContext.requireUserId()); }
    @GetMapping("/public/media/{id}")
    public ResponseEntity<FileSystemResource> publicImage(@PathVariable String id) { return image(id,null); }
    @GetMapping("/media/{id}/cover")
    public ResponseEntity<FileSystemResource> privateCover(@PathVariable String id) {
        return cover(id, AuthContext.requireUserId());
    }
    @GetMapping("/public/media/{id}/cover")
    public ResponseEntity<FileSystemResource> publicCover(@PathVariable String id) {
        return cover(id, null);
    }
    private ResponseEntity<FileSystemResource> cover(String id, Long userId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(new FileSystemResource(media.coverFile(id, userId)));
    }
    private ResponseEntity<FileSystemResource> image(String id,Long userId) {
        var asset=media.accessible(id,userId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.contentType)).cacheControl(CacheControl.noStore())
            .header("Accept-Ranges", "bytes").header("X-Content-Type-Options","nosniff").body(new FileSystemResource(media.file(asset)));
    }
}
