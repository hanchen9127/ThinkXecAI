package com.thinkxecai.model.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadDocumentRequest {

    private String kbId;
    private MultipartFile file;

}
