package com.aiassistant.agent.mapper;

import com.aiassistant.common.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT COALESCE(MAX(seq_no), 0) FROM message WHERE conversation_id = #{conversationId}")
    int getMaxSeqNo(@Param("conversationId") Long conversationId);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} AND seq_no > #{afterSeqNo} ORDER BY seq_no ASC LIMIT #{limit}")
    List<Message> selectAfterSeqNo(@Param("conversationId") Long conversationId,
                                   @Param("afterSeqNo") int afterSeqNo,
                                   @Param("limit") int limit);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY seq_no DESC LIMIT #{limit}")
    List<Message> selectRecent(@Param("conversationId") Long conversationId,
                               @Param("limit") int limit);
}
