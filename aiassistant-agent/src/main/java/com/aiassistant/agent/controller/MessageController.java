package com.aiassistant.agent.controller;

import com.aiassistant.agent.service.MessageService;
import com.aiassistant.common.dto.MessageDTO;
import com.aiassistant.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/{conversationId}/messages")
    public R<List<MessageDTO>> listMessages(
            @PathVariable Long conversationId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long lastMsgId,
            @RequestParam(defaultValue = "50") int size) {
        return R.ok(messageService.getMessagesByConversation(conversationId, lastMsgId, size));
    }
}
