package com.aiassistant.agent.service;

import com.aiassistant.common.dto.ConversationDTO;
import com.aiassistant.common.entity.Conversation;
import com.aiassistant.common.exception.BusinessException;
import com.aiassistant.common.result.ErrorCode;
import com.aiassistant.agent.mapper.ConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;

    public String createConversation(Long userId, String firstMessage) {
        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setTitle(truncateTitle(firstMessage));
        conv.setMode("chat");
        conv.setStatus(1);
        conversationMapper.insert(conv);
        return String.valueOf(conv.getId());
    }

    public List<ConversationDTO> listConversations(Long userId, Long lastId, int size) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
               .eq(Conversation::getStatus, 1);

        if (lastId != null) {
            wrapper.lt(Conversation::getId, lastId);
        }

        wrapper.orderByDesc(Conversation::getUpdatedAt)
               .last("LIMIT " + size);

        return conversationMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .toList();
    }

    public Conversation getById(Long id) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null || conv.getStatus() != 1) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conv;
    }

    public void deleteConversation(Long id, Long userId) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        conversationMapper.updateStatus(id, 2);
    }

    public void rename(Long id, Long userId, String title) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        conversationMapper.updateTitle(id, title);
    }

    public void updateTitleIfNeeded(Long id, String firstMessage) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv != null && "新对话".equals(conv.getTitle())) {
            conversationMapper.updateTitle(id, truncateTitle(firstMessage));
        }
    }

    private ConversationDTO toDTO(Conversation conv) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conv.getId());
        dto.setTitle(conv.getTitle());
        dto.setMode(conv.getMode());
        dto.setUpdatedAt(conv.getUpdatedAt());
        return dto;
    }

    private String truncateTitle(String text) {
        if (text == null) return "新对话";
        return text.length() > 30 ? text.substring(0, 30) + "..." : text;
    }
}
