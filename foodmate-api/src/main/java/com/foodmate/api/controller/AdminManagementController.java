package com.foodmate.api.controller;

import com.foodmate.application.account.AdminManagementService;
import com.foodmate.application.account.PersonalDataService;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
    public ApiResponse<Map<String, Object>> uploadKnowledge(
            @RequestPart("file") MultipartFile file, HttpServletRequest request)
            throws java.io.IOException {
        var operator = requireAnyRole(request, "admin", "superadmin");
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
        return ok(Map.of("documentId", id));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<Map<String, Object>> userStatus(
            @PathVariable long id, @RequestBody StatusRequest body, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        management.updateUserStatus(
                id, body.status(), operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(Map.of("updated", true, "status", body.status()));
    }

    @PostMapping("/users/{id}/sessions/revoke-all")
    public ApiResponse<Map<String, Object>> revokeSessions(
            @PathVariable long id, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        int changed =
                management.revokeSessions(
                        id, operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(Map.of("revoked", changed));
    }

    @PatchMapping("/tools/{name}/status")
    public ApiResponse<Map<String, Object>> toolStatus(
            @PathVariable String name,
            @RequestBody StatusRequest body,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        management.updateToolStatus(
                name,
                body.status(),
                operator.userId(),
                TraceContextHolder.currentOrNew().traceId());
        return ok(Map.of("updated", true, "status", body.status()));
    }

    @PatchMapping("/knowledge/{id}/status")
    public ApiResponse<Map<String, Object>> knowledgeStatus(
            @PathVariable long id, @RequestBody StatusRequest body, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        management.updateKnowledgeStatus(
                id, body.status(), operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(Map.of("updated", true, "status", body.status()));
    }

    @PostMapping("/resources/{type}/{id}/restore")
    public ApiResponse<Map<String, Object>> restore(
            @PathVariable String type, @PathVariable String id, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        long numericId;
        try {
            numericId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("resource id must be numeric");
        }
        management.restore(
                type, numericId, operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(Map.of("restored", true));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }

    public record StatusRequest(String status) {}
}
