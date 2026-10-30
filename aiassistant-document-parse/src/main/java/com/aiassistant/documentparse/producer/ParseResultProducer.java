package com.aiassistant.documentparse.producer;

import com.aiassistant.common.dto.ParseResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseResultProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendSuccess(Long documentId, Long userId, int chunkCount) {
        ParseResultMessage msg = new ParseResultMessage();
        msg.setDocumentId(documentId);
        msg.setUserId(userId);
        msg.setStatus("SUCCESS");
        msg.setChunkCount(chunkCount);

        kafkaTemplate.send("document-parse-result", String.valueOf(documentId), msg);
        log.info("Sent parse success result for document {}", documentId);
    }

    public void sendFailure(Long documentId, Long userId, String errorMessage) {
        ParseResultMessage msg = new ParseResultMessage();
        msg.setDocumentId(documentId);
        msg.setUserId(userId);
        msg.setStatus("FAILED");
        msg.setErrorMessage(errorMessage);

        kafkaTemplate.send("document-parse-result", String.valueOf(documentId), msg);
        log.info("Sent parse failure result for document {}", documentId);
    }
}
