package com.agenticrag.api;

import com.agenticrag.api.dto.InitUploadRequest;
import com.agenticrag.model.UserUploadFile;
import com.agenticrag.ports.RedisBitmapStore;
import com.agenticrag.service.JwtService;
import com.agenticrag.service.OfflineUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@Validated
public class UploadController {
    private final OfflineUploadService offlineUploadService;
    private final RedisBitmapStore redisBitmapStore;
    private final JwtService jwtService;

    public UploadController(OfflineUploadService offlineUploadService, RedisBitmapStore redisBitmapStore, JwtService jwtService) {
        this.offlineUploadService = offlineUploadService;
        this.redisBitmapStore = redisBitmapStore;
        this.jwtService = jwtService;
    }

    @PostMapping("/init")
    public Map<String, Object> initUpload(@Valid @org.springframework.web.bind.annotation.RequestBody InitUploadRequest request) {
        offlineUploadService.initUpload(request.getFileId(), request.getTotalChunks(), request.getOriginalFileName());
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        result.put("fileId", request.getFileId());
        result.put("totalChunks", request.getTotalChunks());
        return result;
    }

    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        boolean accepted = offlineUploadService.uploadChunk(fileId, totalChunks, chunkIndex, file.getBytes());
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("accepted", accepted);
        result.put("chunkIndex", chunkIndex);
        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam("fileId") String fileId, @RequestParam("chunkIndex") int chunkIndex) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("uploaded", redisBitmapStore.isUploaded(fileId, chunkIndex));
        return result;
    }

    @GetMapping("/my-files")
    public Map<String, Object> myFiles(HttpServletRequest request) {
        String userId = jwtService.parseUserIdFromHeader(request.getHeader("Authorization"));
        if (userId == null || userId.trim().isEmpty()) {
            throw new AuthFailedException();
        }
        List<UserUploadFile> files = offlineUploadService.listUserFiles(userId);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        result.put("userId", userId);
        result.put("files", files);
        return result;
    }

    @DeleteMapping("/my-files")
    public Map<String, Object> deleteFile(HttpServletRequest request, @RequestParam("fileMd5") String fileMd5) {
        String userId = jwtService.parseUserIdFromHeader(request.getHeader("Authorization"));
        if (userId == null || userId.trim().isEmpty()) {
            throw new AuthFailedException();
        }
        String fileId = userId + ":" + (fileMd5 == null ? "" : fileMd5.toLowerCase());
        boolean deleted = offlineUploadService.deleteUserFile(userId, fileId);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", deleted);
        result.put("fileId", fileId);
        return result;
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class AuthFailedException extends RuntimeException {
    }
}
