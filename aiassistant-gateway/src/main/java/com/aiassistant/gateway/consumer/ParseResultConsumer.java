package com.aiassistant.gateway.consumer;

import com.aiassistant.common.dto.ParseResultMessage;
import com.aiassistant.gateway.service.DocumentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseResultConsumer {

    private final DocumentNotificationService notificationService;

    @KafkaListener(topics = "document-parse-result", groupId = "gateway-group")
    public void onParseResult(ParseResultMessage message) {
        log.info("Received parse result for document {}: status={}", message.getDocumentId(), message.getStatus());
        notificationService.sendNotification(message);
    }
}
