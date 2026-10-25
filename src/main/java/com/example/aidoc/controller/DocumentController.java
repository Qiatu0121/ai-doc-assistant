package com.example.aidoc.controller;

import com.example.aidoc.common.Result;
import com.example.aidoc.dto.ChunkVO;
import com.example.aidoc.dto.DocumentVO;
import com.example.aidoc.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档模块接口。
 * V1 不做登录鉴权，作为本地单用户工具使用。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    /** 上传并同步完成解析、分块、向量写入。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.upload(file));
    }

    @GetMapping
    public Result<List<DocumentVO>> list() {
        return Result.ok(documentService.list());
    }

    @GetMapping("/{id}")
    public Result<DocumentVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.get(id));
    }

    @GetMapping("/{id}/chunks")
    public Result<List<ChunkVO>> chunks(@PathVariable Long id) {
        return Result.ok(documentService.getChunks(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok(null);
    }
}
