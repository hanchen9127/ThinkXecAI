package com.thinkxecai.controller;

import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.UpdateChatSessionRequest;
import com.thinkxecai.model.vo.ChatSessionVO;
import com.thinkxecai.service.ChatSessionFacadeService;
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
@RequestMapping("/api/chat-sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionFacadeService chatSessionFacadeService;

    @GetMapping
    public ApiResponse<List<ChatSessionVO>> listChatSessions() {
        return ApiResponse.success(chatSessionFacadeService.listChatSessions());
    }

    @GetMapping("/{chatSessionId}")
    public ApiResponse<ChatSessionVO> getChatSession(@PathVariable UUID chatSessionId) {
        return ApiResponse.success(chatSessionFacadeService.getChatSession(chatSessionId));
    }

    @GetMapping("/agent/{agentId}")
    public ApiResponse<List<ChatSessionVO>> listChatSessionsByAgent(@PathVariable UUID agentId) {
        return ApiResponse.success(chatSessionFacadeService.listChatSessionsByAgent(agentId));
    }

    @PostMapping
    public ApiResponse<ChatSessionVO> createChatSession(@Valid @RequestBody CreateChatSessionRequest request) {
        return ApiResponse.success(chatSessionFacadeService.createChatSession(request));
    }

    @PatchMapping("/{chatSessionId}")
    public ApiResponse<ChatSessionVO> updateChatSession(@PathVariable UUID chatSessionId,
                                                        @Valid @RequestBody UpdateChatSessionRequest request) {
        return ApiResponse.success(chatSessionFacadeService.updateChatSession(chatSessionId, request));
    }

    @PostMapping("/{chatSessionId}/stop")
    public ApiResponse<Boolean> stopChatSession(@PathVariable UUID chatSessionId) {
        return ApiResponse.success(chatSessionFacadeService.stopChatSession(chatSessionId));
    }

    @DeleteMapping("/{chatSessionId}")
    public ApiResponse<Void> deleteChatSession(@PathVariable UUID chatSessionId) {
        chatSessionFacadeService.deleteChatSession(chatSessionId);
        return ApiResponse.success();
    }

}
