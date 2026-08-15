package com.foodmate.application.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.food.port.out.ApprovalRequestRepository;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.food.service.FoodLogService;
import com.foodmate.application.food.service.MealPlanService;
import com.foodmate.application.food.service.impl.ApprovalServiceImpl;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ApprovalServiceImplTest {
    private static final Instant FUTURE = Instant.now().plusSeconds(3600);
    private static final Instant PAST = Instant.parse("2026-08-12T11:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearTrace() {
        TraceContextHolder.clear();
    }

    @Test
    void proposalIsCreatedAndIdempotentReplayReturnsOriginalRequest() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        MealPlanService plans = org.mockito.Mockito.mock(MealPlanService.class);
        AtomicReference<ApprovalRequestRepository.ApprovalWrite> write = new AtomicReference<>();
        ApprovalRequestRepository.ApprovalSnapshot[] stored =
                new ApprovalRequestRepository.ApprovalSnapshot[1];
        when(repository.findByIdempotency(7L, "proposal-1")).thenReturn(null, stored[0]);
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            write.set(invocation.getArgument(0));
                            return 1;
                        })
                .when(repository)
                .insert(any());
        when(repository.findOwned(7L, 100L))
                .thenAnswer(
                        invocation -> {
                            ApprovalRequestRepository.ApprovalWrite value = write.get();
                            stored[0] = snapshot(value, "pending", FUTURE);
                            return stored[0];
                        });
        ApprovalService service = service(repository, plans, ids(100L, 101L));
        ObjectNode parameters = parameters("validated");

        ApprovalService.ProposalView first = service.propose(7L, command(parameters));
        when(repository.findByIdempotency(7L, "proposal-1")).thenReturn(stored[0]);
        ApprovalService.ProposalView replay = service.propose(7L, command(parameters));

        assertEquals(first, replay);
        verify(repository).insert(any());
        verify(repository, org.mockito.Mockito.times(1)).insertAudit(any());
    }

    @Test
    void proposalRejectsChangedParametersForExistingIdempotencyKey() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        when(repository.findByIdempotency(7L, "same-key"))
                .thenReturn(
                        snapshot(
                                new ApprovalRequestRepository.ApprovalWrite(
                                        100L,
                                        7L,
                                        null,
                                        null,
                                        "meal_plan",
                                        55L,
                                        "save_plan",
                                        "different",
                                        "req_test",
                                        "trace_test",
                                        "same-key",
                                        FUTURE),
                                "pending",
                                FUTURE));
        ApprovalService service =
                service(repository, org.mockito.Mockito.mock(MealPlanService.class), ids(100L));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.propose(7L, command("same-key", parameters("other"))));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(repository, never()).insert(any());
        verify(repository, never()).insertAudit(any());
    }

    @Test
    void confirmMovesPendingProposalToConfirmedAndAuditsIt() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        ObjectNode parameters = parameters("validated");
        ApprovalRequestRepository.ApprovalSnapshot pending =
                snapshotFor(parameters, "pending", FUTURE);
        ApprovalRequestRepository.ApprovalSnapshot confirmed =
                snapshotFor(parameters, "confirmed", FUTURE);
        when(repository.findOwned(7L, 100L)).thenReturn(pending, confirmed);
        when(repository.markConfirmed(eq(7L), eq(100L), any())).thenReturn(1);
        ApprovalService service =
                service(repository, org.mockito.Mockito.mock(MealPlanService.class), ids(101L));

        ApprovalService.ProposalView result = service.confirm(7L, 100L, parameters);

        assertEquals("confirmed", result.status());
        verify(repository).markConfirmed(eq(7L), eq(100L), any());
        verify(repository).insertAudit(any());
    }

    @Test
    void expiredProposalCannotBeConfirmed() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        ObjectNode parameters = parameters("validated");
        when(repository.findOwned(7L, 100L)).thenReturn(snapshotFor(parameters, "pending", PAST));
        ApprovalService service =
                service(repository, org.mockito.Mockito.mock(MealPlanService.class), ids(101L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.confirm(7L, 100L, parameters));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(repository).markExpired(eq(7L), eq(100L), any());
        verify(repository, never()).markConfirmed(any(Long.class), any(Long.class), any());
    }

    @Test
    void confirmedProposalCannotBeReconfirmedAfterExpiry() {
        ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
        ObjectNode parameters = parameters("validated");
        when(repository.findOwned(7L, 100L)).thenReturn(snapshotFor(parameters, "confirmed", PAST));
        ApprovalService service = service(repository, mock(MealPlanService.class), ids(101L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.confirm(7L, 100L, parameters));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(repository).markExpired(eq(7L), eq(100L), any());
    }

    @Test
    void executeClaimsConfirmedProposalBeforeSavingAndReplayDoesNotSaveAgain() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        MealPlanService plans = org.mockito.Mockito.mock(MealPlanService.class);
        ObjectNode parameters = parameters("validated");
        ApprovalRequestRepository.ApprovalSnapshot confirmed =
                snapshotFor(parameters, "confirmed", FUTURE);
        ApprovalRequestRepository.ApprovalSnapshot executed =
                snapshotFor(parameters, "executed", FUTURE);
        when(repository.findOwned(7L, 100L)).thenReturn(confirmed, executed);
        when(repository.markExecuted(eq(7L), eq(100L), any())).thenReturn(1);
        ApprovalService service = service(repository, plans, ids(101L, 102L));

        ApprovalService.ExecuteView first = service.execute(7L, 100L, parameters);
        ApprovalService.ExecuteView replay = service.execute(7L, 100L, parameters);

        assertEquals("executed", first.status());
        assertEquals(first, replay);
        InOrder order = org.mockito.Mockito.inOrder(repository, plans);
        order.verify(repository).markExecuted(eq(7L), eq(100L), any());
        order.verify(plans).save(eq(7L), eq(55L), org.mockito.ArgumentMatchers.startsWith("plan_"));
        verify(plans).save(eq(7L), eq(55L), org.mockito.ArgumentMatchers.startsWith("plan_"));
        verify(repository).insertAudit(any());
    }

    @Test
    void executeRejectsChangedParametersAndDoesNotSave() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        MealPlanService plans = org.mockito.Mockito.mock(MealPlanService.class);
        ObjectNode original = parameters("validated");
        when(repository.findOwned(7L, 100L)).thenReturn(snapshotFor(original, "confirmed", FUTURE));
        ApprovalService service = service(repository, plans, ids(101L));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.execute(7L, 100L, parameters("changed")));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(plans, never()).save(any(Long.class), any(Long.class));
        verify(repository, never()).markExecuted(any(Long.class), any(Long.class), any());
    }

    @Test
    void executeRejectsUnconfirmedProposalAndDoesNotSave() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        MealPlanService plans = org.mockito.Mockito.mock(MealPlanService.class);
        ObjectNode parameters = parameters("validated");
        when(repository.findOwned(7L, 100L)).thenReturn(snapshotFor(parameters, "pending", FUTURE));
        ApprovalService service = service(repository, plans, ids(101L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.execute(7L, 100L, parameters));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        assertEquals(
                "TOOL_CONFIRMATION_REQUIRED", exception.details().path("tool_error_code").asText());
        verify(plans, never()).save(any(Long.class), any(Long.class), any());
        verify(repository, never()).markExecuted(any(Long.class), any(Long.class), any());
    }

    @Test
    void agentExecutionRejectsMismatchedIdempotencyKey() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        FoodLogService foods = org.mockito.Mockito.mock(FoodLogService.class);
        ObjectNode parameters =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("meal_time", "2026-08-13T04:00:00Z")
                        .put("meal_type", "lunch");
        parameters
                .putArray("items")
                .addObject()
                .put("name", "rice")
                .put("amount", 100)
                .put("unit", "g");
        ApprovalRequestRepository.ApprovalSnapshot confirmed =
                snapshot(
                        new ApprovalRequestRepository.ApprovalWrite(
                                100L,
                                7L,
                                8L,
                                42L,
                                "food_log",
                                null,
                                "create",
                                digest("create", "food_log", null, parameters),
                                "req_test",
                                "trace_test",
                                "food-key",
                                FUTURE),
                        "confirmed",
                        FUTURE);
        when(repository.findOwned(7L, 100L)).thenReturn(confirmed);
        ApprovalService service =
                new ApprovalServiceImpl(
                        repository,
                        org.mockito.Mockito.mock(MealPlanService.class),
                        foods,
                        ids(101L),
                        mapper);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.executeForAgent(7L, 42L, 100L, "different-key", parameters));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        assertEquals(
                "TOOL_IDEMPOTENCY_CONFLICT", exception.details().path("tool_error_code").asText());
        verify(foods, never()).create(any(Long.class), any());
        verify(repository, never()).markExecuted(any(Long.class), any(Long.class), any());
    }

    @Test
    void foodLogApprovalBindsCreatedResourceForReplay() {
        ApprovalRequestRepository repository =
                org.mockito.Mockito.mock(ApprovalRequestRepository.class);
        FoodLogService foods = org.mockito.Mockito.mock(FoodLogService.class);
        ApprovalService service =
                new ApprovalServiceImpl(
                        repository,
                        org.mockito.Mockito.mock(MealPlanService.class),
                        foods,
                        ids(101L, 102L),
                        mapper);
        ObjectNode parameters =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("meal_time", "2026-08-13T04:00:00Z")
                        .put("meal_type", "lunch");
        parameters
                .putArray("items")
                .addObject()
                .put("name", "rice")
                .put("amount", 100)
                .put("unit", "g");
        ApprovalRequestRepository.ApprovalSnapshot confirmed =
                snapshot(
                        new ApprovalRequestRepository.ApprovalWrite(
                                100L,
                                7L,
                                8L,
                                42L,
                                "food_log",
                                null,
                                "create",
                                service.parametersDigest("create", "food_log", null, parameters),
                                "req_test",
                                "trace_test",
                                "food-key",
                                FUTURE),
                        "confirmed",
                        FUTURE);
        ApprovalRequestRepository.ApprovalSnapshot executed =
                new ApprovalRequestRepository.ApprovalSnapshot(
                        100L,
                        7L,
                        8L,
                        42L,
                        "food_log",
                        501L,
                        "create",
                        confirmed.parametersDigest(),
                        "executed",
                        "req_test",
                        "trace_test",
                        "food-key",
                        FUTURE,
                        FUTURE,
                        FUTURE);
        when(repository.findOwned(7L, 100L)).thenReturn(confirmed, executed);
        when(repository.markExecuted(eq(7L), eq(100L), any())).thenReturn(1);
        when(repository.updateExecutedResource(eq(7L), eq(100L), eq(501L), any())).thenReturn(1);
        when(foods.create(eq(7L), any()))
                .thenReturn(
                        new FoodLogService.FoodLogView(
                                501L,
                                8L,
                                42L,
                                Instant.parse("2026-08-13T04:00:00Z"),
                                com.foodmate.shared.food.enums.MealType.LUNCH,
                                null,
                                "agent",
                                1,
                                false,
                                FUTURE,
                                FUTURE,
                                java.util.List.of()));
        ApprovalService.ExecuteView first =
                service.executeForAgent(7L, 42L, 100L, "food-key", parameters);
        ApprovalService.ExecuteView replay =
                service.executeForAgent(7L, 42L, 100L, "food-key", parameters);

        assertEquals(501L, first.resourceId());
        assertEquals(first, replay);
        verify(foods).create(eq(7L), any());
        verify(repository).updateExecutedResource(eq(7L), eq(100L), eq(501L), any());
    }

    @Test
    void rejectPersistsRejectedStateAndReplayIsStable() {
        ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
        ObjectNode parameters = parameters("validated");
        when(repository.findOwned(7L, 100L))
                .thenReturn(snapshotFor(parameters, "pending", FUTURE))
                .thenReturn(snapshotFor(parameters, "rejected", FUTURE));
        when(repository.markRejected(eq(7L), eq(100L), any())).thenReturn(1);

        ApprovalService service = service(repository, mock(MealPlanService.class), ids(101L));
        ApprovalService.ProposalView first = service.reject(7L, 100L, parameters);
        ApprovalService.ProposalView replay = service.reject(7L, 100L, parameters);

        assertEquals("rejected", first.status());
        assertEquals(first, replay);
        verify(repository).markRejected(eq(7L), eq(100L), any());
        verify(repository).insertAudit(any());
    }

    @Test
    void newResourceProposalSupersedesOlderActiveProposal() {
        ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
        ObjectNode parameters = foodUpdateParameters(3L);
        when(repository.findByIdempotency(7L, "update-key")).thenReturn(null);
        when(repository.insert(any())).thenReturn(1);
        when(repository.findOwned(7L, 100L))
                .thenReturn(
                        snapshot(
                                new ApprovalRequestRepository.ApprovalWrite(
                                        100L,
                                        7L,
                                        null,
                                        null,
                                        "food_log",
                                        501L,
                                        "update",
                                        digest("update", "food_log", 501L, parameters),
                                        "req_test",
                                        "trace_test",
                                        "update-key",
                                        FUTURE),
                                "pending",
                                FUTURE));

        ApprovalService service = service(repository, mock(MealPlanService.class), ids(100L, 101L));
        ApprovalService.ProposalView result =
                service.propose(
                        7L,
                        new ApprovalService.ProposalCommand(
                                null,
                                null,
                                "update",
                                "food_log",
                                501L,
                                parameters,
                                "update-key",
                                600));

        assertEquals("pending", result.status());
        verify(repository)
                .markSupersededForResource(
                        eq(7L), eq("food_log"), eq(501L), eq("update"), eq(100L), any());
    }

    @Test
    void failedFoodLogExecutionPersistsFailedApprovalState() {
        ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
        FoodLogService foods = mock(FoodLogService.class);
        ObjectNode parameters = foodInput();
        ApprovalService service =
                new ApprovalServiceImpl(
                        repository, mock(MealPlanService.class), foods, ids(101L, 102L), mapper);
        String digest = service.parametersDigest("create", "food_log", null, parameters);
        ApprovalRequestRepository.ApprovalSnapshot confirmed =
                snapshot(
                        new ApprovalRequestRepository.ApprovalWrite(
                                100L,
                                7L,
                                8L,
                                42L,
                                "food_log",
                                null,
                                "create",
                                digest,
                                "req_test",
                                "trace_test",
                                "food-key",
                                FUTURE),
                        "confirmed",
                        FUTURE);
        when(repository.findOwned(7L, 100L)).thenReturn(confirmed, confirmed);
        when(repository.markExecuted(eq(7L), eq(100L), any())).thenReturn(1);
        when(repository.markFailed(eq(7L), eq(100L), any())).thenReturn(1);
        when(foods.create(eq(7L), any()))
                .thenThrow(new BusinessException(ErrorCode.TOOL_FAILED, "food log write failed"));

        assertThrows(
                BusinessException.class,
                () -> service.executeForAgent(7L, 42L, 100L, "food-key", parameters));

        verify(repository).markFailed(eq(7L), eq(100L), any());
        verify(repository).insertAudit(any());
        verify(repository, never())
                .updateExecutedResource(any(Long.class), any(Long.class), any(Long.class), any());
    }

    @Test
    void foodLogWriterExecutesUpdateDeleteAndRestoreThroughSameApprovalEntryPoint() {
        ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
        FoodLogService foods = mock(FoodLogService.class);
        ApprovalService service =
                new ApprovalServiceImpl(
                        repository,
                        mock(MealPlanService.class),
                        foods,
                        ids(101L, 102L, 103L),
                        mapper);

        ObjectNode update = foodUpdateParameters(3L);
        executeFoodOperation(repository, service, foods, "update", update, 501L, "update-key");
        verify(foods).update(eq(7L), eq(501L), eq(3L), any());

        ObjectNode delete = JsonNodeFactory.instance.objectNode().put("revision", 4L);
        executeFoodOperation(repository, service, foods, "delete", delete, 501L, "delete-key");
        verify(foods).delete(eq(7L), eq(501L), eq(4L), any());

        ObjectNode restore = JsonNodeFactory.instance.objectNode().put("revision", 5L);
        executeFoodOperation(repository, service, foods, "restore", restore, 501L, "restore-key");
        verify(foods).restore(eq(7L), eq(501L), eq(5L), any());
    }

    private void executeFoodOperation(
            ApprovalRequestRepository repository,
            ApprovalService service,
            FoodLogService foods,
            String operation,
            ObjectNode parameters,
            long resourceId,
            String key) {
        String digest = service.parametersDigest(operation, "food_log", resourceId, parameters);
        ApprovalRequestRepository.ApprovalSnapshot confirmed =
                snapshot(
                        new ApprovalRequestRepository.ApprovalWrite(
                                resourceId,
                                7L,
                                null,
                                42L,
                                "food_log",
                                resourceId,
                                operation,
                                digest,
                                "req_test",
                                "trace_test",
                                key,
                                FUTURE),
                        "confirmed",
                        FUTURE);
        when(repository.findOwned(7L, resourceId)).thenReturn(confirmed);
        when(repository.markExecuted(eq(7L), eq(resourceId), any())).thenReturn(1);
        service.executeForAgent(7L, 42L, resourceId, key, parameters);
    }

    private ObjectNode foodInput() {
        ObjectNode value =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("meal_time", "2026-08-13T04:00:00Z")
                        .put("meal_type", "lunch");
        value.putArray("items").addObject().put("name", "rice").put("amount", 100).put("unit", "g");
        return value;
    }

    private ObjectNode foodUpdateParameters(long revision) {
        ObjectNode value = foodInput().put("revision", revision);
        return value;
    }

    private ApprovalService service(
            ApprovalRequestRepository repository, MealPlanService plans, IdGenerator ids) {
        TraceContextHolder.set(TraceContext.of("req_test", "trace_test"));
        return new ApprovalServiceImpl(repository, plans, ids, mapper);
    }

    private ApprovalService.ProposalCommand command(ObjectNode parameters) {
        return command("proposal-1", parameters);
    }

    private ApprovalService.ProposalCommand command(String idempotencyKey, ObjectNode parameters) {
        return new ApprovalService.ProposalCommand(
                null, null, "save_plan", "meal_plan", 55L, parameters, idempotencyKey, 600L);
    }

    private ObjectNode parameters(String status) {
        return JsonNodeFactory.instance.objectNode().put("status", status);
    }

    private ApprovalRequestRepository.ApprovalSnapshot snapshotFor(
            ObjectNode parameters, String status, Instant expiresAt) {
        return snapshot(
                new ApprovalRequestRepository.ApprovalWrite(
                        100L,
                        7L,
                        null,
                        null,
                        "meal_plan",
                        55L,
                        "save_plan",
                        digest("save_plan", "meal_plan", 55L, parameters),
                        "req_test",
                        "trace_test",
                        "proposal-1",
                        expiresAt),
                status,
                expiresAt);
    }

    private ApprovalRequestRepository.ApprovalSnapshot snapshot(
            ApprovalRequestRepository.ApprovalWrite write, String status, Instant expiresAt) {
        return new ApprovalRequestRepository.ApprovalSnapshot(
                write.approvalRequestId(),
                write.userId(),
                write.sessionId(),
                write.agentRunId(),
                write.resourceType(),
                write.resourceId(),
                write.operation(),
                write.parametersDigest(),
                status,
                write.requestId(),
                write.traceId(),
                write.idempotencyKey(),
                expiresAt,
                "confirmed".equals(status) || "executed".equals(status) ? FUTURE : null,
                "executed".equals(status) ? FUTURE : null);
    }

    private String digest(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            mapper.writeValueAsString(
                                                            java.util.Arrays.asList(values))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private IdGenerator ids(long... values) {
        return new IdGenerator() {
            private int index;

            @Override
            public long nextId() {
                return values[Math.min(index++, values.length - 1)];
            }
        };
    }
}
