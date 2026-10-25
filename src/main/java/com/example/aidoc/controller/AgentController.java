package com.example.aidoc.controller;

import com.example.aidoc.agent.AgentService;
import com.example.aidoc.agent.dto.AgentChatRequest;
import com.example.aidoc.agent.dto.AgentChatResponse;
import com.example.aidoc.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 接口。V1 为单轮请求，不保存会话历史。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        return Result.ok(agentService.chat(request.getMessage()));
    }
}
