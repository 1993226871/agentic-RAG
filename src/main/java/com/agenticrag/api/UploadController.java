package com.agenticrag.api;

import com.agenticrag.api.dto.InitUploadRequest;
import com.agenticrag.ports.RedisBitmapStore;
import com.agenticrag.service.OfflineUploadService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@Validated
public class UploadController {
    private final OfflineUploadService offlineUploadService;
    private final RedisBitmapStore redisBitmapStore;

    public UploadController(OfflineUploadService offlineUploadService, RedisBitmapStore redisBitmapStore) {
        this.offlineUploadService = offlineUploadService;
        this.redisBitmapStore = redisBitmapStore;
    }

    @PostMapping("/init")
    public Map<String, Object> initUpload(@Valid @org.springframework.web.bind.annotation.RequestBody InitUploadRequest request) {
        offlineUploadService.initUpload(request.getFileId(), request.getTotalChunks());
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
}
