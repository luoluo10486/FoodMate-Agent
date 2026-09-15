package com.foodmate.api.request.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 账户请求的 JSON 字段契约测试。 */
class AccountRequestJsonContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsSnakeCasePasswordFields() throws Exception {
        var request =
                objectMapper.readValue(
                        "{\"current_password\":\"old-password\",\"new_password\":\"new-password\"}",
                        PasswordChangeRequest.class);

        assertThat(request.currentPassword()).isEqualTo("old-password");
        assertThat(request.newPassword()).isEqualTo("new-password");
    }

    @Test
    void mapsSnakeCasePasswordResetFields() throws Exception {
        var request =
                objectMapper.readValue(
                        "{\"token\":\"reset-token\",\"new_password\":\"new-password\"}",
                        PasswordResetConfirmRequest.class);

        assertThat(request.token()).isEqualTo("reset-token");
        assertThat(request.newPassword()).isEqualTo("new-password");
    }

    @Test
    void mapsSnakeCaseAccountDeletionFields() throws Exception {
        var request =
                objectMapper.readValue(
                        "{\"confirmation\":\"DELETE_MY_ACCOUNT\",\"current_password\":\"old-password\"}",
                        DeletionRequest.class);

        assertThat(request.confirmation()).isEqualTo("DELETE_MY_ACCOUNT");
        assertThat(request.currentPassword()).isEqualTo("old-password");
    }
}
