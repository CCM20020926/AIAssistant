package com.aiassistant.gateway.service;

import com.aiassistant.common.dto.KnowledgeBaseDTO;
import com.aiassistant.common.entity.KnowledgeBase;
import com.aiassistant.common.exception.BusinessException;
import com.aiassistant.common.result.ErrorCode;
import com.aiassistant.gateway.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public Mono<KnowledgeBaseDTO> create(String name, String description, Long userId) {
        return Mono.fromCallable(() -> {
            Long exists = knowledgeBaseMapper.selectCount(new QueryWrapper<KnowledgeBase>()
                    .eq("user_id", userId)
                    .eq("name", name));
            if (exists != null && exists > 0) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EXISTS);
            }
            KnowledgeBase kb = new KnowledgeBase();
            kb.setUserId(userId);
            kb.setName(name);
            kb.setDescription(description);
            kb.setDocCount(0);
            kb.setStatus(1);
            knowledgeBaseMapper.insert(kb);
            return toDTO(kb);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KnowledgeBaseDTO>> list(Long userId) {
        return Mono.fromCallable(() -> {
            List<KnowledgeBase> list = knowledgeBaseMapper.selectList(new QueryWrapper<KnowledgeBase>()
                    .eq("user_id", userId)
                    .orderByDesc("created_at"));
            return list.stream().map(this::toDTO).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(Long id, Long userId) {
        return Mono.fromCallable(() -> {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
            if (kb == null || !kb.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
            }
            knowledgeBaseMapper.deleteById(id);
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private KnowledgeBaseDTO toDTO(KnowledgeBase kb) {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setId(kb.getId());
        dto.setName(kb.getName());
        dto.setDescription(kb.getDescription());
        dto.setDocCount(kb.getDocCount());
        dto.setCreatedAt(kb.getCreatedAt());
        return dto;
    }
}
