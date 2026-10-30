package com.aiassistant.agent.tool;

import com.aiassistant.agent.service.WebSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class WebSearchTool {

    private final WebSearchService webSearchService;

    public WebSearchTool(WebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    @Tool(name = "webSearch",
          description = "联网搜索实时信息。当用户问题涉及时事、最新数据、实时状态、" +
                        "或超出模型知识截止时间的事实时调用。入参 query 必须是结合对话上下文改写后的、" +
                        "语义完整明确、可独立理解的搜索关键词或问题（补全代词、指代、主题、时间等）。")
    public String webSearch(
            @ToolParam(description = "改写后的、语义完整明确的独立搜索查询") String query) {

        log.debug("webSearch tool invoked: rewrittenQuery={}", query);
        return webSearchService.search(query);
    }
}
