package com.foodmate.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.port.out.PersonalDataRepository;
import com.foodmate.application.account.service.PersonalDataService;
import com.foodmate.application.account.service.impl.PersonalDataServiceImpl;
import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class PersonalDataServiceImplTest {
    private PersonalDataRepository store;
    private ObjectStoragePort storage;
    private IdGenerator ids;
    private OperationAuditService audit;
    private PersonalDataServiceImpl service;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(PersonalDataRepository.class);
        storage = Mockito.mock(ObjectStoragePort.class);
        ids = Mockito.mock(IdGenerator.class);
        audit = Mockito.mock(OperationAuditService.class);
        service =
                new PersonalDataServiceImpl(
                        provider(store),
                        provider(storage),
                        provider(ids),
                        provider(audit),
                        new ObjectMapper(),
                        "foodmate-private");
    }

    @Test
    void uploadsDecodedPngAndDoesNotReturnPrivateObjectKey() {
        byte[] image = onePixelPng();
        when(ids.nextId()).thenReturn(101L);

        PersonalDataService.Avatar avatar =
                service.uploadAvatar(
                        7L,
                        "avatar.png",
                        "image/png",
                        image.length,
                        new ByteArrayInputStream(image));

        assertEquals(101L, avatar.avatarAssetId());
        assertEquals("/api/users/me/avatar", avatar.avatarUrl());
        assertEquals("image/png", avatar.mimeType());
        assertEquals(image.length, avatar.sizeBytes());
        verify(storage)
                .put(
                        Mockito.eq("foodmate-private"),
                        Mockito.eq("avatars/7/101.png"),
                        any(),
                        Mockito.eq((long) image.length),
                        Mockito.eq("image/png"));
        verify(store)
                .insertAvatar(
                        Mockito.eq(101L),
                        Mockito.eq(7L),
                        Mockito.eq("avatars/7/101.png"),
                        Mockito.eq("/api/users/me/avatar"),
                        Mockito.eq("image/png"),
                        Mockito.eq((long) image.length),
                        Mockito.eq(1),
                        Mockito.eq(1),
                        Mockito.eq("avatar.png"),
                        anyString());
        verify(store).setAvatarUrl(7L, "/api/users/me/avatar");
    }

    @Test
    void rejectsForgedMimeBeforeObjectStorage() {
        byte[] text = "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadAvatar(
                                        7L,
                                        "avatar.png",
                                        "image/png",
                                        text.length,
                                        new ByteArrayInputStream(text)));

        assertEquals(ErrorCode.USER_AVATAR_INVALID, exception.errorCode());
        verify(storage, never()).put(anyString(), anyString(), any(), anyLong(), anyString());
        verify(store, never())
                .insertAvatar(
                        anyLong(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        anyString(),
                        anyString());
    }

    @Test
    void deletesNewObjectWhenDatabaseCommitFails() {
        byte[] image = onePixelPng();
        when(ids.nextId()).thenReturn(102L);
        doThrow(new IllegalStateException("database unavailable")).when(store).replaceAvatars(7L);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadAvatar(
                                        7L,
                                        "avatar.png",
                                        "image/png",
                                        image.length,
                                        new ByteArrayInputStream(image)));

        assertEquals(ErrorCode.USER_AVATAR_UPLOAD_FAILED, exception.errorCode());
        verify(storage).delete("foodmate-private", "avatars/7/102.png");
        verify(store, never()).setAvatarUrl(anyLong(), anyString());
    }

    @Test
    void failsClosedWhenObjectDeletionFails() {
        when(store.activeAvatarKeys(7L)).thenReturn(List.of("avatars/7/101.png"));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage)
                .delete("foodmate-private", "avatars/7/101.png");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteAvatar(7L));

        assertEquals(ErrorCode.USER_AVATAR_DELETE_FAILED, exception.errorCode());
        verify(store, never()).deleteAvatars(7L);
        verify(store, never()).clearAvatar(7L);
    }

    @Test
    void reportsDownloadFailureWithDownloadErrorCode() {
        when(store.activeAvatar(7L))
                .thenReturn(
                        new PersonalDataRepository.AvatarRow(
                                101L, "avatars/7/101.png", "image/png"));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage)
                .presignedGet(
                        "foodmate-private", "avatars/7/101.png", java.time.Duration.ofMinutes(10));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.avatarDownloadUrl(7L));

        assertEquals(ErrorCode.USER_AVATAR_DOWNLOAD_FAILED, exception.errorCode());
        verify(audit)
                .recordFailure(
                        Mockito.eq(7L),
                        Mockito.eq("avatar"),
                        Mockito.eq("101"),
                        Mockito.eq("account.avatar.download"),
                        Mockito.eq("failed"),
                        Mockito.eq(ErrorCode.USER_AVATAR_DOWNLOAD_FAILED.code()),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.anyMap());
    }

    private static byte[] onePixelPng() {
        return Base64.getDecoder()
                .decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
