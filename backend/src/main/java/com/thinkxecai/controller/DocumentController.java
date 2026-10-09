package com.thinkxecai.controller;

import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.vo.DocumentVO;
import com.thinkxecai.service.DocumentFacadeService;
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

/** POST /api/documents/upload arrives in Phase 8. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentFacadeService documentFacadeService;

    @GetMapping
    public ApiResponse<List<DocumentVO>> listDocuments() {
        return ApiResponse.success(documentFacadeService.listDocuments());
    }

    @GetMapping("/kb/{kbId}")
    public ApiResponse<List<DocumentVO>> listDocumentsByKb(@PathVariable UUID kbId) {
        return ApiResponse.success(documentFacadeService.listDocumentsByKb(kbId));
    }

    @PostMapping
    public ApiResponse<DocumentVO> createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        return ApiResponse.success(documentFacadeService.createDocument(request));
    }

    @PatchMapping("/{documentId}")
    public ApiResponse<DocumentVO> updateDocument(@PathVariable UUID documentId,
                                                  @Valid @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.success(documentFacadeService.updateDocument(documentId, request));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID documentId) {
        documentFacadeService.deleteDocument(documentId);
        return ApiResponse.success();
    }

}
