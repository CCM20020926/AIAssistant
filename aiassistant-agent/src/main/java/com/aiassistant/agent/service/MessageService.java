package com.aiassistant.agent.service;

import com.aiassistant.common.dto.MessageDTO;
import com.aiassistant.common.entity.Message;
import com.aiassistant.agent.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    public Message saveMessage(Long conversationId, Long userId, String role, String content, String meta) {
        int nextSeq = messageMapper.getMaxSeqNo(conversationId) + 1;

        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMeta(meta);
        msg.setSeqNo(nextSeq);
        msg.setTokenCount(0);
        messageMapper.insert(msg);
        return msg;
    }

    public Message saveMessage(Long conversationId, Long userId, String role, String content) {
        return saveMessage(conversationId, userId, role, content, null);
    }

    public List<MessageDTO> getRecentMessages(Long conversationId, int limit) {
        List<Message> messages = messageMapper.selectRecent(conversationId, limit);

        return messages.stream()
                .sorted((a, b) -> a.getSeqNo() - b.getSeqNo())
                .map(this::toDTO)
                .toList();
    }

    public List<MessageDTO> getMessagesAfter(Long conversationId, int afterSeqNo, int limit) {
        return messageMapper.selectAfterSeqNo(conversationId, afterSeqNo, limit).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<MessageDTO> getMessagesByConversation(Long conversationId, Long lastMsgId, int size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId);

        if (lastMsgId != null) {
            wrapper.lt(Message::getId, lastMsgId);
        }

        wrapper.orderByDesc(Message::getSeqNo)
               .last("LIMIT " + size);

        List<Message> messages = messageMapper.selectList(wrapper);

        return messages.stream()
                .sorted((a, b) -> a.getSeqNo() - b.getSeqNo())
                .map(this::toDTO)
                .toList();
    }

    private MessageDTO toDTO(Message msg) {
        MessageDTO dto = new MessageDTO();
        dto.setId(msg.getId());
        dto.setRole(msg.getRole());
        dto.setContent(msg.getContent());
        dto.setMeta(msg.getMeta());
        dto.setSeqNo(msg.getSeqNo());
        dto.setCreatedAt(msg.getCreatedAt());
        return dto;
    }
}
