package com.thinkxecai.model.response;

import com.thinkxecai.model.vo.ChatMessageVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateChatMessageResponse {

    private ChatMessageVO chatMessage;

}
