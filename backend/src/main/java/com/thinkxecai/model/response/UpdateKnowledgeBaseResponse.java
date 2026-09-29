package com.thinkxecai.model.response;

import com.thinkxecai.model.vo.KnowledgeBaseVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateKnowledgeBaseResponse {

    private KnowledgeBaseVO knowledgeBase;

}
