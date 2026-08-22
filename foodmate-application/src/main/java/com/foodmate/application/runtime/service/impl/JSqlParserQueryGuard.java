package com.foodmate.application.runtime.service.impl;

import com.foodmate.application.runtime.service.SqlQueryGuard;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.FieldView;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.Scope;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.TableView;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BooleanValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

/** JSqlParser-backed read-only SQL guard with Java-derived scope predicates. */
@Service
public class JSqlParserQueryGuard implements SqlQueryGuard {
    private static final int MAX_ROWS = 500;
    private static final int TIMEOUT_MS = 5_000;
    private static final Set<String> ALLOWED_FUNCTIONS =
            Set.of("count", "sum", "avg", "min", "max", "coalesce", "date_trunc");

    @Override
    public GuardedQuery guard(
            String statement, SqlSchemaCatalogService.CatalogView catalog, long trustedUserId) {
        if (statement == null
                || statement.isBlank()
                || statement.length() > 8_192
                || trustedUserId <= 0
                || catalog == null
                || containsRejectedSyntax(statement))
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);

        Statement parsed;
        try {
            parsed = CCJSqlParserUtil.parse(statement);
        } catch (JSQLParserException exception) {
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED, "SQL 无法解析");
        }
        if (!(parsed instanceof Select select)
                || parsed instanceof SetOperationList
                || select.getForClause() != null
                || select.getForUpdateTable() != null)
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);

        CatalogIndex index = new CatalogIndex(catalog);
        try {
            Set<String> tables = new TablesNamesFinder<Void>().getTables(parsed);
            for (String table : tables) {
                if (index.findTable(table) == null)
                    throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            }
            List<Object> parameters = new ArrayList<>();
            inspectSelect(select, index, parameters, trustedUserId, Set.of());
            String guardedSql = select.toString();
            return new GuardedQuery(guardedSql, List.copyOf(parameters), MAX_ROWS, TIMEOUT_MS);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED, "SQL AST 校验失败");
        }
    }

    private static void inspectSelect(
            Select select,
            CatalogIndex index,
            List<Object> parameters,
            long trustedUserId,
            Set<String> cteNames) {
        if (select instanceof SetOperationList)
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
        if (select instanceof ParenthesedSelect parenthesed) {
            inspectSelect(parenthesed.getSelect(), index, parameters, trustedUserId, cteNames);
            return;
        }
        if (!(select instanceof PlainSelect plain))
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);

        Set<String> nestedCteNames = new HashSet<>(cteNames);
        List<WithItem<?>> withItems = plain.getWithItemsList();
        if (withItems != null) {
            for (WithItem<?> withItem : withItems) {
                String alias = normalize(withItem.getUnquotedAliasName());
                if (alias == null || withItem.getParenthesedStatement() == null)
                    throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
                nestedCteNames.add(alias);
                if (!(withItem.getParenthesedStatement() instanceof Select cte))
                    throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
                inspectSelect(cte, index, parameters, trustedUserId, nestedCteNames);
                index.registerCte(alias, cte);
            }
        }

        if (plain.getFromItem() != null && !(plain.getFromItem() instanceof Table))
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
        List<Table> sourceTables = sourceTables(plain);
        Map<String, TableView> visibleTables = new HashMap<>();
        for (Table source : sourceTables) {
            String sourceName = normalize(source.getUnquotedName());
            if (sourceName == null) continue;
            TableView table = index.findTable(source);
            if (table == null) throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            visibleTables.put(sourceName, table);
            String alias =
                    source.getAlias() == null ? null : normalize(source.getAlias().getName());
            if (alias != null) visibleTables.put(alias, table);
        }

        for (SelectItem<?> item : plain.getSelectItems()) {
            if (item.getExpression() instanceof AllColumns
                    || item.getExpression() instanceof AllTableColumns)
                throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            if (item.getExpression() instanceof ParenthesedSelect)
                throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
            inspectExpression(item.getExpression(), visibleTables, index);
        }
        inspectExpression(plain.getWhere(), visibleTables, index);
        if (plain.getJoins() != null) {
            for (Join join : plain.getJoins()) {
                if (!(join.getRightItem() instanceof Table))
                    throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
                inspectExpression(join.getOnExpression(), visibleTables, index);
            }
        }
        if (plain.getGroupBy() != null) {
            inspectExpressionList(
                    plain.getGroupBy().getGroupByExpressionList(), visibleTables, index);
        }
        if (plain.getOrderByElements() != null) {
            plain.getOrderByElements()
                    .forEach(
                            order ->
                                    inspectExpression(order.getExpression(), visibleTables, index));
        }
        injectScopePredicates(
                plain, sourceTables, index, parameters, trustedUserId, nestedCteNames);
    }

    private static void inspectExpression(
            Expression expression, Map<String, TableView> visibleTables, CatalogIndex index) {
        if (expression == null) return;
        expression.accept(new SqlExpressionInspector(visibleTables, index), null);
    }

    private static void inspectExpressionList(
            ExpressionList<?> expressions,
            Map<String, TableView> visibleTables,
            CatalogIndex index) {
        if (expressions == null) return;
        for (Expression expression : expressions.getExpressions())
            inspectExpression(expression, visibleTables, index);
    }

    private static void injectScopePredicates(
            PlainSelect plain,
            List<Table> sourceTables,
            CatalogIndex index,
            List<Object> parameters,
            long trustedUserId,
            Set<String> cteNames) {
        Expression predicate = plain.getWhere();
        for (Table source : sourceTables) {
            String sourceName = normalize(source.getUnquotedName());
            if (sourceName == null || cteNames.contains(sourceName)) continue;
            TableView table = index.findTable(source);
            if (table == null) throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            if (!index.hasField(table, "is_deleted"))
                throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            String qualifier =
                    source.getAlias() == null ? source.getName() : source.getAlias().getName();
            predicate =
                    and(
                            predicate,
                            new EqualsTo(
                                    new Column(new Table(qualifier), "is_deleted"),
                                    new BooleanValue(false)));
            if (table.scope() == Scope.USER) {
                if (!index.hasField(table, "user_id"))
                    throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
                predicate =
                        and(
                                predicate,
                                new EqualsTo(
                                        new Column(new Table(qualifier), "user_id"),
                                        new JdbcParameter()));
                parameters.add(trustedUserId);
            } else if (table.scope() == Scope.USER_VIA_FOOD_LOG) {
                if (!index.hasField(table, "food_log_id")
                        || !hasFoodLogParentJoin(plain, source, sourceTables, index))
                    throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            } else if (table.scope() == Scope.TENANT) {
                if (!index.hasField(table, "tenant_id"))
                    throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
                predicate =
                        and(
                                predicate,
                                new EqualsTo(
                                        new Column(new Table(qualifier), "tenant_id"),
                                        new JdbcParameter()));
                parameters.add(0L);
            }
        }
        plain.setWhere(predicate);
        if (plain.getLimit() == null)
            plain.setLimit(
                    new net.sf.jsqlparser.statement.select.Limit()
                            .withRowCount(new net.sf.jsqlparser.expression.LongValue(MAX_ROWS)));
        else if (plain.getLimit().getRowCount() == null
                || !isBoundedLimit(plain.getLimit().getRowCount()))
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
    }

    private static boolean hasFoodLogParentJoin(
            PlainSelect plain, Table child, List<Table> sourceTables, CatalogIndex index) {
        Table parent =
                sourceTables.stream()
                        .filter(table -> "food_logs".equals(normalize(table.getUnquotedName())))
                        .findFirst()
                        .orElse(null);
        if (parent == null || index.findTable(parent) == null) return false;
        if (plain.getJoins() == null) return false;
        String childQualifier = qualifier(child);
        String parentQualifier = qualifier(parent);
        for (Join join : plain.getJoins()) {
            if (join.getOnExpression() == null) continue;
            if (containsFoodLogKeyEquality(join.getOnExpression(), childQualifier, parentQualifier))
                return true;
        }
        return false;
    }

    private static boolean containsFoodLogKeyEquality(
            Expression expression, String childQualifier, String parentQualifier) {
        if (expression == null) return false;
        final boolean[] matched = {false};
        expression.accept(
                new net.sf.jsqlparser.expression.ExpressionVisitorAdapter<Void>() {
                    @Override
                    public <S> Void visit(EqualsTo equalsTo, S context) {
                        if ((matchesColumn(
                                                equalsTo.getLeftExpression(),
                                                childQualifier,
                                                "food_log_id")
                                        && matchesColumn(
                                                equalsTo.getRightExpression(),
                                                parentQualifier,
                                                "food_log_id"))
                                || (matchesColumn(
                                                equalsTo.getRightExpression(),
                                                childQualifier,
                                                "food_log_id")
                                        && matchesColumn(
                                                equalsTo.getLeftExpression(),
                                                parentQualifier,
                                                "food_log_id"))) matched[0] = true;
                        return super.visit(equalsTo, context);
                    }
                },
                null);
        return matched[0];
    }

    private static boolean matchesColumn(Expression expression, String qualifier, String name) {
        if (!(expression instanceof Column column)) return false;
        String columnQualifier =
                column.getTable() == null ? null : normalize(column.getTable().getName());
        return name.equals(normalize(column.getUnquotedColumnName()))
                && qualifier.equals(columnQualifier);
    }

    private static String qualifier(Table table) {
        return normalize(
                table.getAlias() == null ? table.getUnquotedName() : table.getAlias().getName());
    }

    private static boolean isBoundedLimit(Expression expression) {
        return expression instanceof net.sf.jsqlparser.expression.LongValue value
                && value.getValue() > 0
                && value.getValue() <= MAX_ROWS;
    }

    private static Expression and(Expression left, Expression right) {
        return left == null
                ? right
                : new AndExpression(new ParenthesedExpressionList<>(List.of(left)), right);
    }

    private static List<Table> sourceTables(PlainSelect plain) {
        List<Table> tables = new ArrayList<>();
        if (plain.getFromItem() instanceof Table table) tables.add(table);
        if (plain.getJoins() != null)
            for (Join join : plain.getJoins())
                if (join.getRightItem() instanceof Table table) tables.add(table);
        return tables;
    }

    private static boolean containsRejectedSyntax(String statement) {
        return statement.indexOf(';') >= 0
                || statement.contains("--")
                || statement.contains("/*")
                || statement.contains("*/")
                || statement.contains("#");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }

    private static final class SqlExpressionInspector
            extends net.sf.jsqlparser.expression.ExpressionVisitorAdapter<Void> {
        private final Map<String, TableView> visibleTables;
        private final CatalogIndex index;

        private SqlExpressionInspector(Map<String, TableView> visibleTables, CatalogIndex index) {
            this.visibleTables = visibleTables;
            this.index = index;
        }

        @Override
        public <S> Void visit(Column column, S context) {
            String field = normalize(column.getUnquotedColumnName());
            TableView table =
                    column.getTable() == null || column.getTable().getName() == null
                            ? uniqueTableForField(field)
                            : visibleTables.get(normalize(column.getTable().getName()));
            if (table == null || !index.hasField(table, field))
                throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            return null;
        }

        @Override
        public <S> Void visit(JdbcParameter parameter, S context) {
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
        }

        @Override
        public <S> Void visit(ParenthesedSelect select, S context) {
            throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
        }

        @Override
        public <S> Void visit(Function function, S context) {
            String name = normalize(function.getName());
            if (name == null || !ALLOWED_FUNCTIONS.contains(name))
                throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            return super.visit(function, context);
        }

        private TableView uniqueTableForField(String field) {
            TableView match = null;
            for (TableView candidate : visibleTables.values()) {
                if (index.hasField(candidate, field)) {
                    if (match != null && match != candidate)
                        throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
                    match = candidate;
                }
            }
            return match;
        }
    }

    private static final class CatalogIndex {
        private final Map<String, TableView> tables = new HashMap<>();

        private CatalogIndex(SqlSchemaCatalogService.CatalogView catalog) {
            for (TableView table : catalog.tables()) {
                String schema = normalize(table.schemaName());
                String name = normalize(table.tableName());
                if (schema == null || name == null) continue;
                tables.put(schema + "." + name, table);
                tables.putIfAbsent(name, table);
            }
        }

        private TableView findTable(String value) {
            String normalized = normalize(value);
            if (normalized == null) return null;
            int dot = normalized.lastIndexOf('.');
            return dot < 0 ? tables.get(normalized) : tables.get(normalized);
        }

        private TableView findTable(Table table) {
            String schema = normalize(table.getUnquotedSchemaName());
            String name = normalize(table.getUnquotedName());
            if (name == null) return null;
            return schema == null ? tables.get(name) : tables.get(schema + "." + name);
        }

        private void registerCte(String cteName, Select select) {
            Select body =
                    select instanceof ParenthesedSelect parenthesed
                            ? parenthesed.getSelect()
                            : select;
            if (!(body instanceof PlainSelect plain))
                throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
            List<String> physicalNames =
                    new TablesNamesFinder<Void>().getTableList((Statement) body);
            if (physicalNames.size() != 1) throw new BusinessException(ErrorCode.SQL_GUARD_DENIED);
            TableView source = findTable(physicalNames.getFirst());
            if (source == null) throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
            List<FieldView> fields = new ArrayList<>();
            for (SelectItem<?> item : plain.getSelectItems()) {
                if (!(item.getExpression() instanceof Column column))
                    throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
                FieldView field = findField(source, column.getUnquotedColumnName());
                if (field == null) throw new BusinessException(ErrorCode.SQL_SCHEMA_DENIED);
                String projectedName = column.getUnquotedColumnName().toLowerCase(Locale.ROOT);
                if (item.getAlias() != null && item.getAlias().getName() != null)
                    projectedName = normalize(item.getAlias().getName());
                fields.add(
                        new FieldView(
                                projectedName,
                                field.description(),
                                field.dataType(),
                                field.filterable(),
                                field.aggregatable(),
                                field.sortable(),
                                field.sampleSql()));
            }
            tables.put(cteName, new TableView("cte", cteName, source.scope(), fields));
        }

        private FieldView findField(TableView table, String fieldName) {
            String normalized = normalize(fieldName);
            return table.fields().stream()
                    .filter(field -> normalize(field.name()).equals(normalized))
                    .findFirst()
                    .orElse(null);
        }

        private boolean hasField(TableView table, String field) {
            return table.fields().stream().anyMatch(item -> normalize(item.name()).equals(field));
        }
    }
}
