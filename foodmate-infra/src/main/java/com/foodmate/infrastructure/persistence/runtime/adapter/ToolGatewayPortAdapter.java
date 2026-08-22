package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.ToolGatewayPort;
import com.foodmate.infrastructure.persistence.runtime.ToolGatewayMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class ToolGatewayPortAdapter implements ToolGatewayPort {
    private final ToolGatewayMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ToolGatewayPortAdapter(
            ToolGatewayMapper mapper, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean runExists(long runId) {
        return mapper.runExists(runId);
    }

    @Override
    public RunContext runContext(long runId) {
        return mapper.runContext(runId);
    }

    @Override
    public List<JsonNode> executeRead(String statement) {
        return executeRead(statement, List.of(), 5_000);
    }

    @Override
    public List<JsonNode> executeRead(String statement, List<Object> parameters, int timeoutMs) {
        return jdbcTemplate.query(
                connection -> {
                    PreparedStatement prepared = connection.prepareStatement(statement);
                    prepared.setQueryTimeout(Math.max(1, (timeoutMs + 999) / 1000));
                    prepared.setMaxRows(500);
                    for (int index = 0; index < parameters.size(); index++)
                        prepared.setObject(index + 1, parameters.get(index));
                    return prepared;
                },
                resultSet -> {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    List<JsonNode> rows = new ArrayList<>();
                    while (resultSet.next()) {
                        var row = objectMapper.createObjectNode();
                        for (int column = 1; column <= metadata.getColumnCount(); column++) {
                            Object value = resultSet.getObject(column);
                            row.set(
                                    metadata.getColumnLabel(column),
                                    value == null
                                            ? objectMapper.nullNode()
                                            : objectMapper.valueToTree(value));
                        }
                        rows.add(row);
                    }
                    return rows;
                });
    }

    @Override
    public void audit(Audit audit) {
        mapper.audit(audit);
    }
}
