package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.runtime.port.out.ToolGatewayPort;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.CatalogView;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.FieldView;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.Scope;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.TableView;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.application.runtime.service.impl.JSqlParserQueryGuard;
import com.foodmate.application.runtime.service.impl.ToolGatewayServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolGatewayAstGuardTest {
    @Test
    void gatewayExecutesOnlyTheGuardedStatementWithTrustedParameters() {
        ToolGatewayPort store = mock(ToolGatewayPort.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L, 1L));
        when(store.executeRead(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(JsonNodeFactory.instance.objectNode().put("value", 1)));
        SqlSchemaCatalogService catalogs = datasourceId -> catalog();
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        (IdGenerator) () -> 99L,
                        (ApprovalService) null,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        null,
                        new JSqlParserQueryGuard(),
                        catalogs);

        ToolGatewayService.ProposalResult result =
                service.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-1",
                                "42",
                                "sql_read",
                                "v1",
                                new ToolGatewayService.ProposalPayload(
                                        "SELECT meal_time FROM food_logs", "inv-1")));

        assertEquals("succeeded", result.status());
        verify(store)
                .executeRead(anyString(), org.mockito.ArgumentMatchers.eq(List.of(7L)), anyInt());
    }

    private static CatalogView catalog() {
        return new CatalogView(
                1L,
                "catalog-v1",
                List.of(
                        new TableView(
                                "public",
                                "food_logs",
                                Scope.USER,
                                List.of(
                                        field("meal_time"),
                                        field("user_id"),
                                        field("is_deleted")))));
    }

    private static FieldView field(String name) {
        return new FieldView(name, null, "text", true, false, true, null);
    }
}
