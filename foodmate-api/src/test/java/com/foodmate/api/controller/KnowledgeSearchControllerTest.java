package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.knowledge.KnowledgeSearchController;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.knowledge.service.KnowledgeSearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeSearchControllerTest {
    private MockMvc mvc;
    private UserAccountService accounts;
    private KnowledgeSearchService search;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        search = Mockito.mock(KnowledgeSearchService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new KnowledgeSearchController(accounts, search))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void authenticatedUserReceivesSafeCitation() throws Exception {
        when(accounts.requireSessionUser("session")).thenReturn(user());
        when(search.search(anyString()))
                .thenReturn(
                        new KnowledgeSearchService.SearchResult(
                                List.of(
                                        new KnowledgeSearchService.Citation(
                                                7, "chunk-7", "Guide", "v2", "Protein", "safe"))));

        mvc.perform(
                        post("/api/knowledge-base/search")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"query\":\"protein\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.citations[0].document_id", is(7)))
                .andExpect(jsonPath("$.data.citations[0].snippet", is("safe")));
    }

    @Test
    void unauthenticatedUserCannotSearch() throws Exception {
        mvc.perform(
                        post("/api/knowledge-base/search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"query\":\"protein\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTH_REQUIRED")));
    }

    private UserAccountService.UserRecord user() {
        return new UserAccountService.UserRecord(
                2L, "user", "user@example.com", "hash", "user", "user", "active");
    }
}
