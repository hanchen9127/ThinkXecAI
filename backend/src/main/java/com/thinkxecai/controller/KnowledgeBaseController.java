package com.thinkxecai.controller;

import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.model.request.CreateKnowledgeBaseRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import com.thinkxecai.model.vo.KnowledgeBaseVO;
import com.thinkxecai.service.KnowledgeBaseFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseFacadeService knowledgeBaseFacadeService;

    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> listKnowledgeBases() {
        return ApiResponse.success(knowledgeBaseFacadeService.listKnowledgeBases());
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseVO> createKnowledgeBase(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseFacadeService.createKnowledgeBase(request));
    }

    @PatchMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseVO> updateKnowledgeBase(@PathVariable UUID knowledgeBaseId,
                                                            @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseFacadeService.updateKnowledgeBase(knowledgeBaseId, request));
    }

    @DeleteMapping("/{knowledgeBaseId}")
    public ApiResponse<Void> deleteKnowledgeBase(@PathVariable UUID knowledgeBaseId) {
        knowledgeBaseFacadeService.deleteKnowledgeBase(knowledgeBaseId);
        return ApiResponse.success();
    }

}
