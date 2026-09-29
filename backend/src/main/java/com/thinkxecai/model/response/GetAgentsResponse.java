package com.thinkxecai.model.response;

import com.thinkxecai.model.vo.AgentVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetAgentsResponse {

    private List<AgentVO> agents;

}
