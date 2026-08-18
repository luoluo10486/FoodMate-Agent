package com.foodmate.infrastructure.persistence.knowledge;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeMapper {
    @Insert(
            "INSERT INTO knowledge_documents(document_id,title,source_type,status,version,storage_key,created_by,updated_by) VALUES (#{documentId},#{title},'admin_upload','uploaded','1',#{storageKey},#{operatorId},#{operatorId})")
    void insertDocument(
            @Param("documentId") long documentId,
            @Param("title") String title,
            @Param("storageKey") String storageKey,
            @Param("operatorId") long operatorId);

    @Update(
            "UPDATE knowledge_documents SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE document_id=#{documentId} AND is_deleted=FALSE")
    int updateStatus(
            @Param("documentId") long documentId,
            @Param("status") String status,
            @Param("operatorId") long operatorId);
}
