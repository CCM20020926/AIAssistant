package com.aiassistant.documentparse.consumer;

import com.aiassistant.common.dto.ParseTaskMessage;
import com.aiassistant.documentparse.service.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseTaskConsumer {

    private final DocumentParseService parseService;

    @KafkaListener(topics = "document-parse-task", groupId = "document-parse-group")
    public void onParseTask(ParseTaskMessage message) {
        log.info("Received parse task for document {}", message.getDocumentId());
        try {
            parseService.execute(message);
        } catch (Exception e) {
            log.error("Failed to process parse task for document {}", message.getDocumentId(), e);
        }
    }
}
