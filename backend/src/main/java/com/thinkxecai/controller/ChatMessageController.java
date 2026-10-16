package com.thinkxecai.controller;

import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.ChatMessageFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat-messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageFacadeService chatMessageFacadeService;

    @GetMapping("/session/{sessionId}")
    public ApiResponse<List<ChatMessageVO>> listChatMessagesBySession(@PathVariable UUID sessionId) {
        return ApiResponse.success(chatMessageFacadeService.listChatMessagesBySession(sessionId));
    }

    @PostMapping
    public ApiResponse<ChatMessageVO> createChatMessage(@Valid @RequestBody CreateChatMessageRequest request) {
        return ApiResponse.success(chatMessageFacadeService.createChatMessage(request));
    }

    @DeleteMapping("/{chatMessageId}")
    public ApiResponse<Void> deleteChatMessage(@PathVariable UUID chatMessageId) {
        chatMessageFacadeService.deleteChatMessage(chatMessageId);
        return ApiResponse.success();
    }

}
