package com.thinkxecai.model.response;

import com.thinkxecai.model.vo.ChatMessageVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetChatMessagesResponse {

    private List<ChatMessageVO> chatMessages;

}
