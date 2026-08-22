package com.foodmate.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.application.account.service.AdminManagementService;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.account.service.impl.AdminManagementServiceImpl;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.runtime.enums.ToolStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminManagementServiceImplTest {
    private AdminManagementRepository store;
    private OperationAuditService audit;
    private AdminManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        store = mock(AdminManagementRepository.class);
        audit = mock(OperationAuditService.class);
        service = new AdminManagementServiceImpl(store, audit, new ObjectMapper());
        when(audit.reserve(
                        any(Long.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any()))
                .thenReturn(1);
    }

    @Test
    void operatorCannotWrite() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.updateUserStatus(
                                        9,
                                        UserStatus.DISABLED,
                                        command(UserRole.OPERATOR, 1, "operator-1")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
        verify(store, never()).updateUserStatus(9, UserStatus.DISABLED, 2, 1);
    }

    @Test
    void adminCanUpdateOrdinaryTool() {
        when(store.findTool("calculator"))
                .thenReturn(
                        new AdminManagementRepository.ToolSnapshot(
                                "calculator", "low", "active", 1));
        when(store.updateToolStatus("calculator", ToolStatus.DISABLED, 2, 1)).thenReturn(1);

        AdminManagementService.ManagementResult result =
                service.updateToolStatus(
                        "calculator", ToolStatus.DISABLED, command(UserRole.ADMIN, 1, "tool-1"));

        assertEquals("disabled", result.status());
        assertEquals(2, result.revision());
        verify(store).updateToolStatus("calculator", ToolStatus.DISABLED, 2, 1);
    }

    @Test
    void adminCannotUpdateHighRiskTool() {
        when(store.findTool("food_log_writer"))
                .thenReturn(
                        new AdminManagementRepository.ToolSnapshot(
                                "food_log_writer", "high", "active", 1));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.updateToolStatus(
                                        "food_log_writer",
                                        ToolStatus.DISABLED,
                                        command(UserRole.ADMIN, 1, "tool-high-1")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
        verify(store, never()).updateToolStatus(any(), any(), any(Long.class), any(Long.class));
    }

    @Test
    void superadminHighRiskToolRequiresMatchingConfirmation() {
        when(store.findTool("food_log_writer"))
                .thenReturn(
                        new AdminManagementRepository.ToolSnapshot(
                                "food_log_writer", "high", "active", 1));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.updateToolStatus(
                                        "food_log_writer",
                                        ToolStatus.DISABLED,
                                        command(UserRole.SUPERADMIN, 1, "tool-high-2")));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(store, never()).updateToolStatus(any(), any(), any(Long.class), any(Long.class));
    }

    @Test
    void staleUserRevisionIsRejectedBeforeUpdate() {
        when(store.findUser(9))
                .thenReturn(new AdminManagementRepository.UserSnapshot(9, "user", "active", 2));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.updateUserStatus(
                                        9,
                                        UserStatus.DISABLED,
                                        command(UserRole.ADMIN, 1, "user-1")));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(store, never())
                .updateUserStatus(any(Long.class), any(), any(Long.class), any(Long.class));
        verify(audit)
                .recordFailure(
                        any(Long.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any());
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutSecondMutation() {
        AdminManagementRepository.UserSnapshot snapshot =
                new AdminManagementRepository.UserSnapshot(9, "user", "active", 1);
        when(store.findUser(9)).thenReturn(snapshot);
        when(store.updateUserStatus(9, UserStatus.DISABLED, 2, 1)).thenReturn(1);
        var stored =
                new com.foodmate.application.common.port.out.OperationAuditPort.IdempotencyRecord
                        [1];
        when(audit.findIdempotency(2, "user-2")).thenAnswer(invocation -> stored[0]);
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            stored[0] =
                                    new com.foodmate.application.common.port.out.OperationAuditPort
                                            .IdempotencyRecord(
                                            invocation.getArgument(4),
                                            "success",
                                            "{\"changed\":true,\"status\":\"disabled\",\"affected\":0,\"revision\":2}");
                            return 1;
                        })
                .when(audit)
                .reserve(
                        any(Long.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any());

        service.updateUserStatus(9, UserStatus.DISABLED, command(UserRole.ADMIN, 1, "user-2"));

        AdminManagementService.ManagementResult result =
                service.updateUserStatus(
                        9, UserStatus.DISABLED, command(UserRole.ADMIN, 1, "user-2"));

        assertEquals(2, result.revision());
        verify(store).findUser(9);
        verify(store).updateUserStatus(9, UserStatus.DISABLED, 2, 1);
    }

    private AdminWriteCommand command(UserRole role, long revision, String key) {
        return new AdminWriteCommand(2, role, "trace-1", key, revision, false, null);
    }
}
