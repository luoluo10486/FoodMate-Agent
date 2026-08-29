package com.foodmate.api.controller.knowledge;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.knowledge.KnowledgeSearchRequest;
import com.foodmate.api.response.knowledge.KnowledgeSearchResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.knowledge.service.KnowledgeSearchService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向用户的公共知识库检索接口。 */
@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeSearchController extends AuthenticatedControllerSupport {
    private final KnowledgeSearchService search;

    public KnowledgeSearchController(UserAccountService accounts, KnowledgeSearchService search) {
        super(accounts);
        this.search = search;
    }

    @PostMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(
            @Valid @RequestBody KnowledgeSearchRequest body, HttpServletRequest request) {
        user(request);
        KnowledgeSearchService.SearchResult result = search.search(body.query());
        return ok(
                new KnowledgeSearchResponse(
                        result.citations().stream()
                                .map(
                                        citation ->
                                                new KnowledgeSearchResponse.Citation(
                                                        citation.documentId(),
                                                        citation.citationId(),
                                                        citation.title(),
                                                        citation.version(),
                                                        citation.sectionPath(),
                                                        citation.snippet()))
                                .toList()));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
