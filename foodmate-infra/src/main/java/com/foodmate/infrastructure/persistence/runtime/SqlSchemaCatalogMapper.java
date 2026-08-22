package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.SqlSchemaCatalogRepository.CatalogField;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Reads only active read-only datasource catalog rows; connection references never leave infra. */
@Mapper
public interface SqlSchemaCatalogMapper {
    @Select(
            "SELECT c.datasource_id AS datasourceId,"
                    + " to_char(MAX(c.updated_at) OVER (PARTITION BY c.datasource_id),"
                    + " 'YYYYMMDDHH24MISSMS') AS catalogVersion,"
                    + " c.schema_name AS schemaName,c.table_name AS tableName,"
                    + " c.field_name AS fieldName,c.field_desc AS fieldDescription,"
                    + " c.data_type AS dataType,c.is_sensitive AS sensitive,c.sample_sql AS sampleSql"
                    + " FROM schema_catalogs c JOIN data_sources d ON d.datasource_id=c.datasource_id"
                    + " AND d.status='active' AND d.readonly=TRUE AND d.is_deleted=FALSE"
                    + " WHERE c.datasource_id=#{datasourceId} AND c.is_deleted=FALSE"
                    + " ORDER BY c.schema_name,c.table_name,c.field_name")
    List<CatalogField> findActiveFields(@Param("datasourceId") long datasourceId);
}
