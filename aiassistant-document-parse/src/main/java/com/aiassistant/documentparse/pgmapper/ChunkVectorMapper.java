package com.aiassistant.documentparse.pgmapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChunkVectorMapper {

    @Insert("INSERT INTO chunk_vector (document_id, knowledge_base_id, chunk_index, content, minio_path, embedding) " +
            "VALUES (#{documentId}, #{knowledgeBaseId}, #{chunkIndex}, #{content}, #{minioPath}, " +
            "#{embedding, typeHandler=com.aiassistant.documentparse.pgmapper.VectorTypeHandler}::vector) " +
            "ON CONFLICT (document_id, chunk_index) DO UPDATE SET " +
            "content = EXCLUDED.content, minio_path = EXCLUDED.minio_path, embedding = EXCLUDED.embedding")
    int insertChunkVector(@Param("documentId") Long documentId,
                          @Param("knowledgeBaseId") Long knowledgeBaseId,
                          @Param("chunkIndex") int chunkIndex,
                          @Param("content") String content,
                          @Param("minioPath") String minioPath,
                          @Param("embedding") float[] embedding);
}
