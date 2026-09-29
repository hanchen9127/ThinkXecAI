package com.thinkxecai.model.request;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateDocumentRequest {

    private String name;
    private Map<String, Object> metadata;

}
