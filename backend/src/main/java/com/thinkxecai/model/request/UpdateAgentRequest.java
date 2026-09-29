package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatOptions;
import lombok.Data;

import java.util.List;

@Data
public class UpdateAgentRequest {

    private String name;
    private String description;
    private String systemPrompt;
    private String model;
    private List<String> allowedTools;
    private List<String> allowedKbs;
    private ChatOptions chatOptions;

}
