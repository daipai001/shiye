package com.shiye.user.module.article.controller;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.article.domain.dto.ArticleCollectRequest;
import com.shiye.user.module.article.domain.dto.ArticleDeleteByUrlRequest;
import com.shiye.user.module.article.domain.vo.ArticleChunkVO;
import com.shiye.user.module.article.domain.vo.ArticleCollectResponse;
import com.shiye.user.module.article.domain.vo.ArticleDetailVO;
import com.shiye.user.module.article.domain.vo.ArticleReindexResponse;
import com.shiye.user.module.article.domain.vo.ArticleStatsVO;
import com.shiye.user.module.article.domain.vo.ArticleSummaryVO;
import com.shiye.user.module.article.domain.vo.ArticleUploadResponse;
import com.shiye.user.module.article.service.ArticleIngestService;
import com.shiye.user.module.article.service.ArticleQueryService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/articles")
public class ArticleController {

    private final ArticleQueryService articleQueryService;
    private final ArticleIngestService articleIngestService;

    public ArticleController(ArticleQueryService articleQueryService,
                             ArticleIngestService articleIngestService) {
        this.articleQueryService = articleQueryService;
        this.articleIngestService = articleIngestService;
    }

    @GetMapping
    public Result<PageResult<ArticleSummaryVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long folderId) {
        return Result.ok(articleQueryService.list(
                UserContext.requireUserId(), page, pageSize, q, status, sourceType,
                tagId, folderId));
    }

    @GetMapping("/stats")
    public Result<ArticleStatsVO> stats() {
        return Result.ok(articleQueryService.stats(UserContext.requireUserId()));
    }

    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable long id) {
        return Result.ok(articleQueryService.detail(UserContext.requireUserId(), id));
    }

    @GetMapping("/{id}/chunks")
    public Result<PageResult<ArticleChunkVO>> chunks(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(articleQueryService.chunks(
                UserContext.requireUserId(), id, page, pageSize));
    }

    @PostMapping("/collect")
    public Result<ArticleCollectResponse> collect(
            @Valid @RequestBody ArticleCollectRequest request) {
        long userId = UserContext.requireUserId();
        if (request.url() == null || request.url().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "url 不能为空");
        }
        ArticleIngestService.IngestOutcome outcome;
        if ("URL".equals(request.mode())) {
            outcome = articleIngestService.collectUrl(userId, request.url(), request.title());
        } else {
            if (request.content() == null || request.content().isBlank()) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "正文不能为空");
            }
            outcome = articleIngestService.collectPage(
                    userId, request.url(), request.title(), request.content());
        }
        return Result.ok(new ArticleCollectResponse(
                String.valueOf(outcome.articleId()), "READY", outcome.duplicate()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ArticleUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请选择要上传的文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }
        ArticleIngestService.UploadOutcome outcome = articleIngestService.upload(
                UserContext.requireUserId(),
                file.getOriginalFilename(),
                file.getContentType(),
                bytes,
                title
        );
        return Result.ok(new ArticleUploadResponse(
                String.valueOf(outcome.fileAssetId()),
                String.valueOf(outcome.articleId()),
                "READY",
                outcome.duplicate()
        ));
    }

    @PostMapping("/{id}/reindex")
    public Result<ArticleReindexResponse> reindex(@PathVariable long id) {
        long userId = UserContext.requireUserId();
        articleIngestService.reindex(userId, id);
        return Result.ok(new ArticleReindexResponse(String.valueOf(id), "PENDING"));
    }

    @PostMapping("/delete-by-url")
    public Result<Void> deleteByUrl(@Valid @RequestBody ArticleDeleteByUrlRequest request) {
        articleQueryService.deleteByUrl(UserContext.requireUserId(), request.url());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        articleQueryService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
