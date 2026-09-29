package com.thinkxecai.model.response;

import com.thinkxecai.model.vo.ChatSessionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateChatSessionResponse {

    private ChatSessionVO chatSession;

}
