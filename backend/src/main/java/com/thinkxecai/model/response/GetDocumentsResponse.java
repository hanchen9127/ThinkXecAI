package com.thinkxecai.model.response;

import com.thinkxecai.model.vo.DocumentVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetDocumentsResponse {

    private List<DocumentVO> documents;

}
