package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.knowledge.KnowledgeController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeControllerTest {
    private UserAccountService accounts;
    private KnowledgeService knowledge;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        knowledge = Mockito.mock(KnowledgeService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new KnowledgeController(accounts, knowledge))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void adminCanUploadDocument() throws Exception {
        when(accounts.requireSessionUser("admin-session")).thenReturn(user("admin"));
        when(knowledge.upload(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        any(InputStream.class),
                        anyString()))
                .thenReturn(42L);
        MockMultipartFile file =
                new MockMultipartFile("file", "note.md", "text/markdown", "hello".getBytes());

        mvc.perform(
                        multipart("/api/admin/knowledge")
                                .file(file)
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "admin-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document_id", is(42)));
    }

    @Test
    void adminCanUpdateDocumentStatus() throws Exception {
        when(accounts.requireSessionUser("admin-session")).thenReturn(user("admin"));

        mvc.perform(
                        patch("/api/admin/knowledge/42/status")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "admin-session"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"indexed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated", is(true)))
                .andExpect(jsonPath("$.data.status", is("indexed")));

        verify(knowledge)
                .updateStatus(
                        anyLong(), eq(KnowledgeDocumentStatus.INDEXED), anyLong(), anyString());
    }

    @Test
    void operatorCannotManageDocuments() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));

        mvc.perform(
                        patch("/api/admin/knowledge/42/status")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"indexed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    private UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                2L, role, role + "@example.com", "hash", role, role, "active");
    }
}
