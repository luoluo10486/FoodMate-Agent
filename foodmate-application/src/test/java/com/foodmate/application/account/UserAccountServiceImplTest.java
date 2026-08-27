package com.foodmate.application.account;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.account.port.out.UserAccountRepository;
import com.foodmate.application.account.service.impl.UserAccountServiceImpl;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class UserAccountServiceImplTest {
    @Test
    void resetPasswordRecordsPasswordChangeAudit() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(repository.resetTokenUser(anyString())).thenReturn(7L);
        UserAccountServiceImpl service = service(repository, audit);

        service.resetPassword("reset-token", "new-password");

        verify(repository).changePassword(eq(7L), anyString());
        verify(repository).consumeResetToken(anyString());
        verify(audit)
                .record(
                        eq(7L),
                        eq("user"),
                        eq("7"),
                        eq("user.password.change"),
                        eq("success"),
                        isNull(),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void invalidResetTokenRecordsFailureAudit() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(repository.resetTokenUser(anyString())).thenReturn(null);
        UserAccountServiceImpl service = service(repository, audit);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.resetPassword("expired-token", "new-password"));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.NOT_FOUND, exception.errorCode());
        verify(audit)
                .recordFailure(
                        isNull(),
                        eq("user"),
                        isNull(),
                        eq("user.password.change"),
                        eq("failed"),
                        eq("NOT_FOUND"),
                        isNull(),
                        isNull(),
                        any());
    }

    private UserAccountServiceImpl service(
            UserAccountRepository repository, OperationAuditService audit) {
        ObjectProvider<UserAccountRepository> storeProvider = mock(ObjectProvider.class);
        ObjectProvider<IdGenerator> idProvider = mock(ObjectProvider.class);
        ObjectProvider<OperationAuditService> auditProvider = mock(ObjectProvider.class);
        when(storeProvider.getIfAvailable()).thenReturn(repository);
        when(idProvider.getIfAvailable()).thenReturn(() -> 100L);
        when(auditProvider.getIfAvailable()).thenReturn(audit);
        return new UserAccountServiceImpl(storeProvider, idProvider, auditProvider);
    }
}
