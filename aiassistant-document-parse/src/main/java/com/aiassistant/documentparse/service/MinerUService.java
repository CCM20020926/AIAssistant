package com.aiassistant.documentparse.service;

import com.aiassistant.common.exception.BusinessException;
import com.aiassistant.common.result.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Iterator;

@Slf4j
@Service
public class MinerUService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mineru.nginx-url:http://127.0.0.1:80/api/document/parse}")
    private String nginxUrl;

    public ParseResult parse(byte[] fileContent, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("backend", "pipeline");
        body.add("return_md", "true");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        log.info("Calling MinerU via Nginx: {}, file: {}", nginxUrl, fileName);
        ResponseEntity<String> response = restTemplate.exchange(
                nginxUrl, HttpMethod.POST, requestEntity, String.class);

        String responseBody = response.getBody();
        log.info("MinerU HTTP status={}, body length={}", response.getStatusCode(),
                responseBody == null ? 0 : responseBody.length());
        log.debug("MinerU raw response (first 2000 chars): {}",
                responseBody == null ? "null" : responseBody.substring(0, Math.min(2000, responseBody.length())));

        return parseResponse(responseBody);
    }

    private ParseResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            log.info("MinerU response top-level fields={}", fieldNames(root));

            JsonNode errorNode = root.get("error");
            if (errorNode != null && !errorNode.isNull() && !errorNode.asText().isEmpty()) {
                log.error("MinerU parse error: {}", errorNode.asText());
                throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED);
            }

            ParseResult result = new ParseResult();

            // MinerU 3.x nests per-file output under results.<filename>; older versions use flat top-level fields.
            JsonNode fileResult = firstFileResult(root.get("results"));
            JsonNode source = fileResult != null ? fileResult : root;

            JsonNode mdNode = source.get("md_content");
            if (mdNode == null || mdNode.isNull()) {
                log.warn("MinerU response has NO usable 'md_content' field; top-level fields={}", fieldNames(root));
            } else {
                result.setMarkdown(mdNode.asText());
            }

            JsonNode middleNode = source.get("middle_json");
            if (middleNode != null && !middleNode.isNull()) {
                result.setMiddleJson(middleNode.toString());
            }

            log.info("MinerU parse completed, markdown length: {}",
                    result.getMarkdown() != null ? result.getMarkdown().length() : 0);
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse MinerU response: {}", responseBody, e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED);
        }
    }

    private JsonNode firstFileResult(JsonNode results) {
        if (results == null || !results.isObject()) {
            return null;
        }
        Iterator<JsonNode> it = results.elements();
        while (it.hasNext()) {
            JsonNode fileResult = it.next();
            if (fileResult != null && fileResult.hasNonNull("md_content")) {
                return fileResult;
            }
        }
        return null;
    }

    private String fieldNames(JsonNode root) {
        java.util.List<String> names = new java.util.ArrayList<>();
        root.fieldNames().forEachRemaining(names::add);
        return names.toString();
    }

    @Data
    public static class ParseResult {
        private String markdown;
        private String middleJson;
    }
}
