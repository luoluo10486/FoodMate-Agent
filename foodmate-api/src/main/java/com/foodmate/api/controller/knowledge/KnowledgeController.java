package com.foodmate.api.controller.knowledge;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.knowledge.KnowledgeStatusRequest;
import com.foodmate.api.response.account.StatusUpdateResponse;
import com.foodmate.api.response.knowledge.DocumentUploadResponse;
import com.foodmate.api.response.knowledge.KnowledgeUploadBatchDetailResponse;
import com.foodmate.api.response.knowledge.KnowledgeUploadBatchResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin")
public class KnowledgeController extends AuthenticatedControllerSupport {
    private final KnowledgeService knowledge;

    public KnowledgeController(UserAccountService accounts, KnowledgeService knowledge) {
        super(accounts);
        this.knowledge = knowledge;
    }

    @PostMapping(value = "/knowledge", consumes = "multipart/form-data")
    public ApiResponse<DocumentUploadResponse> upload(
            @RequestPart("file") MultipartFile file, HttpServletRequest request)
            throws IOException {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        if (file.isEmpty() || file.getSize() > 20 * 1024 * 1024)
            throw new IllegalArgumentException("unsupported document");
        String traceId = TraceContextHolder.currentOrNew().traceId();
        long id =
                knowledge.upload(
                        operator.userId(),
                        file.getOriginalFilename() == null
                                ? "document"
                                : file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize(),
                        file.getInputStream(),
                        traceId);
        return ok(new DocumentUploadResponse(id));
    }

    @PatchMapping("/knowledge/{id}/status")
    public ApiResponse<StatusUpdateResponse> updateStatus(
            @PathVariable long id,
            @Valid @RequestBody KnowledgeStatusRequest body,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        knowledge.updateStatus(
                id, body.status(), operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, body.status().code()));
    }

    @PostMapping(value = "/knowledge-documents/upload-batches", consumes = "multipart/form-data")
    public ApiResponse<KnowledgeUploadBatchResponse> uploadBatch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("source_type") String sourceType,
            @RequestPart("source_name") String sourceName,
            @RequestPart("source_version") String sourceVersion,
            @RequestPart("license_notice") String licenseNotice,
            @RequestPart("idempotency_key") String idempotencyKey,
            HttpServletRequest request)
            throws IOException {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        if (files == null || files.isEmpty() || files.size() > 20)
            throw new IllegalArgumentException("invalid knowledge import batch");
        List<KnowledgeService.ImportFile> imports =
                files.stream()
                        .map(
                                file -> {
                                    try {
                                        return new KnowledgeService.ImportFile(
                                                file.getOriginalFilename() == null
                                                        ? "document"
                                                        : file.getOriginalFilename(),
                                                file.getContentType() == null
                                                        ? "application/octet-stream"
                                                        : file.getContentType(),
                                                file.getSize(),
                                                file.getInputStream());
                                    } catch (IOException exception) {
                                        throw new IllegalArgumentException(
                                                "unable to read uploaded document", exception);
                                    }
                                })
                        .toList();
        long batchId =
                knowledge.uploadBatch(
                        operator.userId(),
                        new KnowledgeService.ImportBatch(
                                idempotencyKey,
                                sourceType,
                                sourceName,
                                sourceVersion,
                                licenseNotice,
                                imports),
                        TraceContextHolder.currentOrNew().traceId());
        return ok(new KnowledgeUploadBatchResponse(batchId, "uploaded"));
    }

    @PostMapping("/knowledge-documents/{id}/publish")
    public ApiResponse<StatusUpdateResponse> publish(
            @PathVariable long id, HttpServletRequest request) {
        return visibility(id, "published", request);
    }

    @PostMapping("/knowledge-documents/{id}/disable")
    public ApiResponse<StatusUpdateResponse> disable(
            @PathVariable long id, HttpServletRequest request) {
        return visibility(id, "disabled", request);
    }

    @PostMapping("/knowledge-documents/{id}/restore")
    public ApiResponse<StatusUpdateResponse> restore(
            @PathVariable long id, HttpServletRequest request) {
        return visibility(id, "draft", request);
    }

    @PostMapping("/knowledge-documents/{id}/delete")
    public ApiResponse<StatusUpdateResponse> delete(
            @PathVariable long id, HttpServletRequest request) {
        return visibility(id, "deleted", request);
    }

    private ApiResponse<StatusUpdateResponse> visibility(
            long id, String value, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        knowledge.changeVisibility(
                id, value, operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, value));
    }

    @GetMapping("/knowledge-upload-batches/{batchId}")
    public ApiResponse<KnowledgeUploadBatchDetailResponse> batch(
            @PathVariable long batchId, HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        return ok(new KnowledgeUploadBatchDetailResponse(knowledge.batch(batchId)));
    }

    @PostMapping("/knowledge-upload-batches/{batchId}/documents/{itemId}/retry")
    public ApiResponse<StatusUpdateResponse> retry(
            @PathVariable long batchId, @PathVariable long itemId, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        knowledge.retryItem(
                batchId, itemId, operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, "pending"));
    }

    @GetMapping(
            value = "/knowledge-upload-batches/{batchId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable long batchId,
            @org.springframework.web.bind.annotation.RequestParam(
                            value = "lastEventId",
                            required = false)
                    Long lastEventId,
            HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        SseEmitter emitter = new SseEmitter(120_000L);
        var executor = Executors.newSingleThreadScheduledExecutor();
        String headerCursor = request.getHeader("Last-Event-ID");
        long initialCursor = lastEventId == null ? parseEventId(headerCursor) : lastEventId;
        final long[] cursor = {Math.max(0, initialCursor)};
        executor.scheduleWithFixedDelay(
                () -> {
                    try {
                        for (var event : knowledge.batchEvents(batchId, cursor[0])) {
                            emitter.send(
                                    SseEmitter.event()
                                            .id(Long.toString(event.eventId()))
                                            .name(event.eventType())
                                            .data(event.payload()));
                            cursor[0] = event.eventId();
                        }
                    } catch (Exception error) {
                        emitter.completeWithError(error);
                        executor.shutdown();
                    }
                },
                0,
                300,
                TimeUnit.MILLISECONDS);
        emitter.onCompletion(executor::shutdown);
        emitter.onTimeout(executor::shutdown);
        return emitter;
    }

    private long parseEventId(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
