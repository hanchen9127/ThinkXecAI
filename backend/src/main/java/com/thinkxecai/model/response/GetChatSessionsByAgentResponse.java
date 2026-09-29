package com.thinkxecai.model.response;

import com.thinkxecai.model.vo.ChatSessionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetChatSessionsByAgentResponse {

    private List<ChatSessionVO> chatSessions;

}
