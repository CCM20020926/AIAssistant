package com.aiassistant.agent.tool;

import com.aiassistant.agent.entity.RetrievedChunk;
import com.aiassistant.agent.service.KnowledgeRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

@Slf4j
public class KnowledgeRetrievalTool {

    private static final int TOP_K = 10;

    private final Long knowledgeBaseId;
    private final KnowledgeRetrievalService retrievalService;

    public KnowledgeRetrievalTool(Long knowledgeBaseId, KnowledgeRetrievalService retrievalService) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.retrievalService = retrievalService;
    }

    @Tool(name = "retrieveKnowledge",
          description = "从当前知识库中检索与问题相关的文档内容。当用户的问题涉及事实性资料、" +
                        "或可能来源于知识库文档时调用。入参 query 必须是结合对话上下文改写后的、" +
                        "语义完整明确、可脱离上下文独立理解的检索问题（补全代词、指代、主题、时间等）。")
    public String retrieveKnowledge(
            @ToolParam(description = "改写后的、语义完整明确的独立检索问题") String query) {

        log.debug("retrieveKnowledge tool invoked: kbId={}, rewrittenQuery={}", knowledgeBaseId, query);

        List<RetrievedChunk> chunks = retrievalService.retrieve(knowledgeBaseId, query, TOP_K);
        if (chunks.isEmpty()) {
            log.debug("retrieveKnowledge: no relevant chunk found for kbId={}", knowledgeBaseId);
            return "知识库中未检索到与该问题相关的内容。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下是从知识库检索到的 ").append(chunks.size()).append(" 条相关文档片段：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append('[').append(i + 1).append("] ")
              .append("(documentId=").append(c.documentId())
              .append(", chunkIndex=").append(c.chunkIndex())
              .append(", score=").append(String.format("%.4f", c.score())).append(")\n")
              .append(c.content()).append("\n\n");
        }
        return sb.toString();
    }
}
