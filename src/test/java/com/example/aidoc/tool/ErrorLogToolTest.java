package com.example.aidoc.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误日志分析工具单元测试。
 */
class ErrorLogToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorLogTool tool = new ErrorLogTool(objectMapper);

    @Test
    void analyzesExceptionAndConnectionRefused() throws Exception {
        String log = "2026-10-25 10:00:00 ERROR java.lang.NullPointerException "
                + "at com.example.Service.run(Service.java:10)\n"
                + "Caused by: java.net.ConnectException: Connection refused";

        JsonNode result = objectMapper.readTree(tool.analyze(log, 5));

        assertTrue(result.get("success").asBoolean());
        assertEquals(1, result.get("levels").get("ERROR").asInt());
        assertTrue(result.get("exceptionTypes").toString().contains("NullPointerException"));
        assertTrue(result.get("matchedKeywords").toString().contains("connection refused"));
        assertTrue(result.get("suggestions").size() > 0);
    }

    @Test
    void rejectsBlankLog() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.analyze("   ", 5));
    }

    @Test
    void rejectsInvalidMaxLines() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.analyze("ERROR something", 0));
    }
}
