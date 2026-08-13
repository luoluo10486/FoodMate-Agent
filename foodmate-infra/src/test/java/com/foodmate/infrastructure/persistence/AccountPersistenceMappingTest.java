package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.account.AuthRefreshTokenPo;
import com.foodmate.infrastructure.persistence.account.UserAvatarAssetPo;
import com.foodmate.infrastructure.persistence.account.UserPo;
import com.foodmate.infrastructure.persistence.account.UserProfilePo;
import com.foodmate.infrastructure.persistence.conversation.MessagePo;
import com.foodmate.infrastructure.persistence.conversation.SessionPo;
import org.junit.jupiter.api.Test;

class AccountPersistenceMappingTest {
    @Test
    void mapsB42TablesToNamedPrimaryKeys() {
        assertMapping(UserPo.class, "users", "user_id");
        assertMapping(UserProfilePo.class, "user_profiles", "profile_id");
        assertMapping(AuthRefreshTokenPo.class, "auth_refresh_tokens", "refresh_token_id");
        assertMapping(UserAvatarAssetPo.class, "user_avatar_assets", "avatar_asset_id");
        assertMapping(SessionPo.class, "sessions", "session_id");
        assertMapping(MessagePo.class, "messages", "message_id");
    }

    private void assertMapping(Class<?> type, String table, String idColumn) {
        TableName tableName = type.getAnnotation(TableName.class);
        assertNotNull(tableName);
        assertEquals(table, tableName.value());
        TableId tableId = type.getDeclaredFields()[0].getAnnotation(TableId.class);
        assertNotNull(tableId);
        assertEquals(idColumn, tableId.value());
    }
}
