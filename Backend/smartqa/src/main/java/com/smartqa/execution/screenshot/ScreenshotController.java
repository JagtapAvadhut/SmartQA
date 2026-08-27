package com.smartqa.execution.screenshot;

import com.smartqa.common.api.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
public class ScreenshotController {

    private final ScreenshotService screenshotService;

    public ScreenshotController(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    @GetMapping("/api/execution-runs/{runId}/screenshots")
    public Mono<ApiResponse<List<ScreenshotMeta>>> list(@PathVariable UUID runId) {
        List<ScreenshotMeta> screenshots = screenshotService.list(runId);
        return Mono.just(ApiResponse.ok("Screenshots fetched", screenshots));
    }

    @GetMapping(value = "/api/screenshots/{screenshotId}", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<Resource>> get(@PathVariable String screenshotId) {
        Path path = screenshotService.filePath(screenshotId);
        if (path == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        Resource resource = new FileSystemResource(path);
        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource));
    }
}
