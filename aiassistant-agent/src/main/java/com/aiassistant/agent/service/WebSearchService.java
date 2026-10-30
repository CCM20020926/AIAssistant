package com.aiassistant.agent.service;

import com.aiassistant.agent.config.WebSearchProperties;
import com.aiassistant.agent.entity.WebSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WebSearchService {

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final WebSearchProperties properties;
    private final RestClient restClient;

    public WebSearchService(WebSearchProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(TAVILY_URL)
                .build();
    }

    /**
     * @return a formatted block: optional synthesized answer + numbered sources.
     */
    public String search(String query) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("webSearch invoked but ai.websearch.api-key is not configured");
            return "联网搜索未配置（缺少 Tavily API Key），无法检索实时信息。";
        }

        log.debug("Tavily search start: query={}, depth={}, maxResults={}",
                query, properties.getSearchDepth(), properties.getMaxResults());

        Map<String, Object> body = Map.of(
                "query", query,
                "search_depth", properties.getSearchDepth(),
                "max_results", properties.getMaxResults(),
                "include_answer", properties.isIncludeAnswer(),
                "include_raw_content", false
        );

        JsonNode root;
        try {
            root = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Tavily search failed for query={}: {}", query, e.getMessage(), e);
            return "联网搜索暂时失败，请稍后重试或直接依据已有知识作答。";
        }

        if (root == null) {
            log.debug("Tavily returned empty body for query={}", query);
            return "联网搜索未返回结果。";
        }

        StringBuilder sb = new StringBuilder();
        JsonNode answerNode = root.get("answer");
        if (answerNode != null && !answerNode.isNull() && !answerNode.asText().isBlank()) {
            sb.append("搜索引擎摘要：\n").append(answerNode.asText()).append("\n\n");
        }

        List<WebSearchResult> results = parseResults(root.get("results"));
        log.debug("Tavily search done: query={}, resultCount={}", query, results.size());

        if (results.isEmpty() && sb.isEmpty()) {
            return "联网搜索未检索到与该问题相关的内容。";
        }

        sb.append("以下是联网检索到的 ").append(results.size()).append(" 条网页结果：\n\n");
        for (int i = 0; i < results.size(); i++) {
            WebSearchResult r = results.get(i);
            sb.append('[').append(i + 1).append("] ")
              .append(r.title()).append("\n")
              .append("URL: ").append(r.url()).append("\n")
              .append("(score=").append(String.format("%.4f", r.score())).append(")\n")
              .append(r.content()).append("\n\n");
            log.debug("WebSearch result[{}] title={}, url={}, score={}, contentLen={}",
                    i, r.title(), r.url(), r.score(),
                    r.content() == null ? 0 : r.content().length());
        }
        return sb.toString();
    }

    private List<WebSearchResult> parseResults(JsonNode resultsNode) {
        List<WebSearchResult> results = new ArrayList<>();
        if (resultsNode == null || !resultsNode.isArray()) {
            return results;
        }
        for (JsonNode node : resultsNode) {
            String title = node.path("title").asText("");
            String url = node.path("url").asText("");
            String content = node.path("content").asText("");
            double score = node.path("score").asDouble(0.0);
            results.add(new WebSearchResult(title, url, content, score));
        }
        return results;
    }
}
