package com.thinkxecai.model.request;

import lombok.Data;

import java.util.Map;

@Data
public class CreateDocumentRequest {

    private String kbId;
    private String name;
    private Map<String, Object> metadata;

}
