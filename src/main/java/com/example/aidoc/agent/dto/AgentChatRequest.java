package com.example.aidoc.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 单轮请求。V1 不保存会话历史。
 */
@Data
public class AgentChatRequest {

    @NotBlank(message = "message 不能为空")
    @Size(max = 4000, message = "message 不能超过 4000 字符")
    private String message;
}
