package com.aiassistant.agent.controller;

import com.aiassistant.agent.service.ConversationService;
import com.aiassistant.common.dto.ConversationDTO;
import com.aiassistant.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/list")
    public R<List<ConversationDTO>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(conversationService.listConversations(userId, lastId, size));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestHeader("X-User-Id") Long userId) {
        conversationService.deleteConversation(id, userId);
        return R.ok();
    }

    @PutMapping("/{id}/title")
    public R<Void> rename(@PathVariable Long id,
                          @RequestHeader("X-User-Id") Long userId,
                          @RequestBody Map<String, String> body) {
        conversationService.rename(id, userId, body.get("title"));
        return R.ok();
    }
}
