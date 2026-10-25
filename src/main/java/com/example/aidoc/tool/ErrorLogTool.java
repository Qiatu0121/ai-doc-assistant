package com.example.aidoc.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具 2：对用户提供的错误日志做确定性分析。
 * 返回异常类型、日志级别、HTTP 状态码、Caused by 链、关键堆栈帧和规则化建议。
 * 是否调用这个工具由模型决定，工具内部不做问题路由。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorLogTool {

    private static final int MAX_INPUT_CHARS = 50_000;
    private static final int ANALYZE_CHARS = 20_000;

    private static final Pattern LEVEL_PATTERN = Pattern.compile(
            "\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "\\b(?:[a-zA-Z_$][\\w$]*\\.)*[A-Za-z_$][\\w$]*(?:Exception|Error)\\b");
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile(
            "(?i)(?:status|http|code)[^0-9]{0,10}([45]\\d{2})");
    private static final Pattern CAUSED_BY_PATTERN = Pattern.compile(
            "(?i)Caused by:\\s*(.+)");
    private static final Pattern STACK_FRAME_PATTERN = Pattern.compile(
            "^\\s*at\\s+(.+)$");

    private static final Map<String, String> KEYWORD_SUGGESTIONS = new LinkedHashMap<>();

    static {
        KEYWORD_SUGGESTIONS.put("outofmemoryerror", "检查堆内存、内存泄漏或 JVM 内存参数");
        KEYWORD_SUGGESTIONS.put("nullpointerexception", "检查空对象、参数校验和初始化顺序");
        KEYWORD_SUGGESTIONS.put("connection refused", "目标服务未启动或端口不可达，检查服务地址和端口");
        KEYWORD_SUGGESTIONS.put("timed out", "检查网络、超时配置和下游服务性能");
        KEYWORD_SUGGESTIONS.put("timeout", "检查网络、超时配置和下游服务性能");
        KEYWORD_SUGGESTIONS.put("connection pool", "检查数据库连接池配置和连接泄漏");
        KEYWORD_SUGGESTIONS.put("hikari", "检查连接池大小、连接超时和未关闭连接");
        KEYWORD_SUGGESTIONS.put("sqlexception", "检查 SQL、事务和数据库连接");
        KEYWORD_SUGGESTIONS.put("deadlock", "检查事务顺序、锁粒度和并发更新");
        KEYWORD_SUGGESTIONS.put("access denied", "检查认证信息和权限配置");
        KEYWORD_SUGGESTIONS.put("unauthorized", "检查 401 认证信息、Token 或签名");
        KEYWORD_SUGGESTIONS.put("forbidden", "检查 403 权限、角色和访问策略");
        KEYWORD_SUGGESTIONS.put("no such file", "检查文件路径、工作目录和部署包内容");
        KEYWORD_SUGGESTIONS.put("permission denied", "检查文件或目录权限");
        KEYWORD_SUGGESTIONS.put("caused by", "沿着 Caused by 链继续定位最底层异常");
        KEYWORD_SUGGESTIONS.put("404", "检查接口路径、路由或资源是否存在");
        KEYWORD_SUGGESTIONS.put("500", "检查服务端异常和上下游调用");
    }

    private final ObjectMapper objectMapper;

    @Tool(
            name = "analyzeErrorLog",
            description = "分析用户提供的错误日志、异常堆栈或报错信息，"
                    + "提取日志级别、异常类型、HTTP 状态码、Caused by 链、"
                    + "关键堆栈帧和常见故障特征。用户粘贴日志时调用。"
    )
    public String analyze(
            @ToolParam(description = "用户提供的完整错误日志或堆栈文本", required = true)
            String logText,
            @ToolParam(description = "最多返回的 Caused by 和堆栈帧数量，1-20，默认 10", required = false)
            Integer maxLines) {

        if (logText == null || logText.isBlank()) {
            throw new IllegalArgumentException("logText 不能为空");
        }
        if (logText.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("日志长度不能超过 " + MAX_INPUT_CHARS + " 字符");
        }

        int lineLimit = maxLines == null ? 10 : maxLines;
        if (lineLimit < 1 || lineLimit > 20) {
            throw new IllegalArgumentException("maxLines 必须在 1-20 之间");
        }

        boolean truncated = logText.length() > ANALYZE_CHARS;
        String text = truncated ? logText.substring(0, ANALYZE_CHARS) : logText;
        String[] lines = text.split("\\R");

        Map<String, Integer> levels = new LinkedHashMap<>();
        Set<String> exceptionTypes = new LinkedHashSet<>();
        Set<Integer> httpStatusCodes = new LinkedHashSet<>();
        List<String> causedByChain = new ArrayList<>();
        List<String> keyStackFrames = new ArrayList<>();

        for (String line : lines) {
            Matcher levelMatcher = LEVEL_PATTERN.matcher(line);
            if (levelMatcher.find()) {
                String level = levelMatcher.group(1).toUpperCase(Locale.ROOT);
                if ("WARNING".equals(level)) {
                    level = "WARN";
                }
                levels.merge(level, 1, Integer::sum);
            }

            Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(line);
            while (exceptionMatcher.find()) {
                exceptionTypes.add(exceptionMatcher.group());
            }

            Matcher statusMatcher = HTTP_STATUS_PATTERN.matcher(line);
            while (statusMatcher.find()) {
                httpStatusCodes.add(Integer.parseInt(statusMatcher.group(1)));
            }

            Matcher causedByMatcher = CAUSED_BY_PATTERN.matcher(line);
            if (causedByMatcher.find() && causedByChain.size() < lineLimit) {
                causedByChain.add(truncate(causedByMatcher.group(1), 300));
            }

            Matcher frameMatcher = STACK_FRAME_PATTERN.matcher(line);
            if (frameMatcher.find() && keyStackFrames.size() < lineLimit) {
                keyStackFrames.add(truncate(frameMatcher.group(1), 300));
            }
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        Set<String> matchedKeywords = new LinkedHashSet<>();
        Set<String> suggestions = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : KEYWORD_SUGGESTIONS.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                matchedKeywords.add(entry.getKey());
                suggestions.add(entry.getValue());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("lineCount", lines.length);
        response.put("truncated", truncated);
        response.put("levels", levels);
        response.put("exceptionTypes", new ArrayList<>(exceptionTypes));
        response.put("httpStatusCodes", new ArrayList<>(httpStatusCodes));
        response.put("causedByChain", causedByChain);
        response.put("keyStackFrames", keyStackFrames);
        response.put("matchedKeywords", new ArrayList<>(matchedKeywords));
        response.put("suggestions", new ArrayList<>(suggestions));
        response.put("summary", buildSummary(lines.length, levels, exceptionTypes.size()));

        log.info("日志分析完成 lines={} exceptions={} truncated={}",
                lines.length, exceptionTypes.size(), truncated);
        return toJson(response);
    }

    private String buildSummary(int lineCount, Map<String, Integer> levels, int exceptionCount) {
        String highest = "INFO";
        if (levels.containsKey("FATAL") || levels.containsKey("ERROR")) {
            highest = "ERROR";
        } else if (levels.containsKey("WARN")) {
            highest = "WARN";
        }
        return "共 " + lineCount + " 行，最高日志级别 " + highest
                + "，识别到 " + exceptionCount + " 个异常类型";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("日志分析结果序列化失败", e);
        }
    }
}
