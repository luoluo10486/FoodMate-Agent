package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.account.PersonalDataService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class AdminManagementController extends AuthenticatedControllerSupport {
    private final JdbcTemplate jdbc;
    private final PersonalDataService personalData;

    public AdminManagementController(UserAccountService accounts, ObjectProvider<JdbcTemplate> jdbc, PersonalDataService personalData) {
        super(accounts);
        this.jdbc = jdbc.getIfAvailable();
        this.personalData = personalData;
    }

    @PostMapping(value = "/knowledge", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> uploadKnowledge(@RequestPart("file") MultipartFile file, HttpServletRequest request) throws java.io.IOException {
        var operator = requireAnyRole(request, "admin", "superadmin");
        if (file.isEmpty() || file.getSize() > 20 * 1024 * 1024) throw new IllegalArgumentException("unsupported document");
        long id = personalData.uploadKnowledge(operator.userId(), file.getOriginalFilename() == null ? "document" : file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream());
        audit(operator.userId(), "knowledge.upload", "knowledge_document", String.valueOf(id));
        return ok(Map.of("documentId", id));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<Map<String, Object>> userStatus(@PathVariable long id, @RequestBody StatusRequest body, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        requireJdbc();
        if (!body.status().equals("active") && !body.status().equals("disabled") && !body.status().equals("locked")) throw new IllegalArgumentException("invalid user status");
        int changed = jdbc.update("UPDATE users SET status=?, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE user_id=? AND is_deleted=FALSE", body.status(), operator.userId(), id);
        if (changed == 0) throw new IllegalArgumentException("user not found");
        audit(operator.userId(), "user.status.update", "user", String.valueOf(id));
        return ok(Map.of("updated", true, "status", body.status()));
    }

    @PostMapping("/users/{id}/sessions/revoke-all")
    public ApiResponse<Map<String, Object>> revokeSessions(@PathVariable long id, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        requireJdbc();
        int changed = jdbc.update("UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE user_id=? AND revoked_at IS NULL AND is_deleted=FALSE", operator.userId(), id);
        audit(operator.userId(), "user.sessions.revoke_all", "user_session", String.valueOf(id));
        return ok(Map.of("revoked", changed));
    }

    @PatchMapping("/tools/{name}/status")
    public ApiResponse<Map<String, Object>> toolStatus(@PathVariable String name, @RequestBody StatusRequest body, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        requireJdbc();
        if (!body.status().equals("active") && !body.status().equals("disabled")) throw new IllegalArgumentException("invalid tool status");
        int changed = jdbc.update("UPDATE tool_registries SET status=?, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE name=? AND is_deleted=FALSE", body.status(), operator.userId(), name);
        if (changed == 0) throw new IllegalArgumentException("tool not found");
        audit(operator.userId(), "tool.status.update", "tool", name);
        return ok(Map.of("updated", true, "status", body.status()));
    }

    @PatchMapping("/knowledge/{id}/status")
    public ApiResponse<Map<String, Object>> knowledgeStatus(@PathVariable long id, @RequestBody StatusRequest body, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        requireJdbc();
        if (!body.status().equals("uploaded") && !body.status().equals("parsed") && !body.status().equals("indexed") && !body.status().equals("disabled")) throw new IllegalArgumentException("invalid document status");
        int changed = jdbc.update("UPDATE knowledge_documents SET status=?, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE document_id=? AND is_deleted=FALSE", body.status(), operator.userId(), id);
        if (changed == 0) throw new IllegalArgumentException("document not found");
        audit(operator.userId(), "knowledge.status.update", "knowledge_document", String.valueOf(id));
        return ok(Map.of("updated", true, "status", body.status()));
    }

    @PostMapping("/resources/{type}/{id}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable String type, @PathVariable String id, HttpServletRequest request) {
        var operator = requireAnyRole(request, "admin", "superadmin");
        requireJdbc();
        String table; String key;
        switch (type) {
            case "user" -> { table = "users"; key = "user_id"; }
            case "knowledge_document" -> { table = "knowledge_documents"; key = "document_id"; }
            case "food_log" -> { table = "food_logs"; key = "food_log_id"; }
            case "meal_plan" -> { table = "meal_plans"; key = "meal_plan_id"; }
            case "message" -> { table = "messages"; key = "message_id"; }
            default -> throw new IllegalArgumentException("unsupported resource type");
        }
        long numericId;
        try { numericId = Long.parseLong(id); } catch (NumberFormatException e) { throw new IllegalArgumentException("resource id must be numeric"); }
        int changed = jdbc.update("UPDATE " + table + " SET is_deleted=FALSE, deleted_at=NULL, deleted_by=NULL, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE " + key + "=? AND is_deleted=TRUE", operator.userId(), numericId);
        if (changed == 0) throw new IllegalArgumentException("resource not found");
        audit(operator.userId(), "resource.restore", type, id);
        return ok(Map.of("restored", true));
    }

    private void requireJdbc() { if (jdbc == null) throw new IllegalStateException("database unavailable"); }
    private void audit(long operatorId, String action, String type, String id) {
        long auditId = jdbc.queryForObject("SELECT COALESCE(MAX(operation_audit_id),0)+1 FROM operation_audits", Long.class);
        jdbc.update("INSERT INTO operation_audits(operation_audit_id,operator_id,trace_id,target_type,target_id,action,result,created_by,updated_by) VALUES (?,?,?,?,?,?, 'success', ?, ?)", auditId, operatorId, TraceContextHolder.currentOrNew().traceId(), type, id, action, operatorId, operatorId);
    }
    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, TraceContextHolder.currentOrNew()); }
    public record StatusRequest(String status) {}
}
