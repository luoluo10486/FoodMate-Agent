package com.foodmate.api.controller;

import com.foodmate.api.request.KnowledgeStatusRequest;
import com.foodmate.api.request.ToolStatusRequest;
import com.foodmate.api.request.UserStatusRequest;
import com.foodmate.api.response.DocumentUploadResponse;
import com.foodmate.api.response.RestoreResponse;
import com.foodmate.api.response.RevokedSessionsResponse;
import com.foodmate.api.response.StatusUpdateResponse;
import com.foodmate.application.account.AdminManagementService;
import com.foodmate.application.account.PersonalDataService;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.account.RestorableResourceType;
import com.foodmate.shared.account.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class AdminManagementController extends AuthenticatedControllerSupport {
    private final PersonalDataService personalData;
    private final AdminManagementService management;

    public AdminManagementController(
            UserAccountService accounts,
            PersonalDataService personalData,
            AdminManagementService management) {
        super(accounts);
        this.personalData = personalData;
        this.management = management;
    }

    @PostMapping(value = "/knowledge", consumes = "multipart/form-data")
    public ApiResponse<DocumentUploadResponse> uploadKnowledge(
            @RequestPart("file") MultipartFile file, HttpServletRequest request)
            throws java.io.IOException {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        if (file.isEmpty() || file.getSize() > 20 * 1024 * 1024)
            throw new IllegalArgumentException("unsupported document");
        long id =
                personalData.uploadKnowledge(
                        operator.userId(),
                        file.getOriginalFilename() == null
                                ? "document"
                                : file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize(),
                        file.getInputStream());
        management.recordAudit(
                operator.userId(),
                TraceContextHolder.currentOrNew().traceId(),
                "knowledge.upload",
                "knowledge_document",
                String.valueOf(id));
        return ok(new DocumentUploadResponse(id));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<StatusUpdateResponse> userStatus(
            @PathVariable long id,
            @Valid @RequestBody UserStatusRequest body,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        management.updateUserStatus(
                id, body.status(), operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, body.status().code()));
    }

    @PostMapping("/users/{id}/sessions/revoke-all")
    public ApiResponse<RevokedSessionsResponse> revokeSessions(
            @PathVariable long id, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        int changed =
                management.revokeSessions(
                        id, operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new RevokedSessionsResponse(changed));
    }

    @PatchMapping("/tools/{name}/status")
    public ApiResponse<StatusUpdateResponse> toolStatus(
            @PathVariable String name,
            @Valid @RequestBody ToolStatusRequest body,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        management.updateToolStatus(
                name,
                body.status(),
                operator.userId(),
                TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, body.status().code()));
    }

    @PatchMapping("/knowledge/{id}/status")
    public ApiResponse<StatusUpdateResponse> knowledgeStatus(
            @PathVariable long id,
            @Valid @RequestBody KnowledgeStatusRequest body,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        management.updateKnowledgeStatus(
                id, body.status(), operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, body.status().code()));
    }

    @PostMapping("/resources/{type}/{id}/restore")
    public ApiResponse<RestoreResponse> restore(
            @PathVariable String type, @PathVariable String id, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        long numericId;
        try {
            numericId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("resource id must be numeric");
        }
        management.restore(
                RestorableResourceType.fromCode(type),
                numericId,
                operator.userId(),
                TraceContextHolder.currentOrNew().traceId());
        return ok(new RestoreResponse(true));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
