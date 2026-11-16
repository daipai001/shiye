package com.shiye.user.module.bookmark.controller;

import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.bookmark.domain.dto.BookmarkImportCreateRequest;
import com.shiye.user.module.bookmark.domain.dto.BookmarkImportRetryRequest;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportCreateResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportItemVO;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportRetryResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportStatusResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportSummaryVO;
import com.shiye.user.module.bookmark.service.BookmarkImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookmark-imports")
public class BookmarkImportController {

    private final BookmarkImportService importService;

    public BookmarkImportController(BookmarkImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public Result<BookmarkImportCreateResponse> create(
            @Valid @RequestBody BookmarkImportCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.ok(importService.create(
                UserContext.requireUserId(), request, idempotencyKey));
    }

    @GetMapping
    public Result<PageResult<BookmarkImportSummaryVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(importService.list(UserContext.requireUserId(), page, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<BookmarkImportSummaryVO> detail(@PathVariable long id) {
        return Result.ok(importService.detail(UserContext.requireUserId(), id));
    }

    @GetMapping("/{id}/items")
    public Result<PageResult<BookmarkImportItemVO>> items(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(importService.items(
                UserContext.requireUserId(), id, page, pageSize, status));
    }

    @PostMapping("/{id}/retry")
    public Result<BookmarkImportRetryResponse> retry(
            @PathVariable long id,
            @RequestBody(required = false) BookmarkImportRetryRequest request) {
        int retried = importService.retry(
                UserContext.requireUserId(), id, request == null ? null : request.itemIds());
        return Result.ok(new BookmarkImportRetryResponse(retried));
    }

    @PostMapping("/{id}/pause")
    public Result<BookmarkImportStatusResponse> pause(@PathVariable long id) {
        return Result.ok(importService.pause(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/resume")
    public Result<BookmarkImportStatusResponse> resume(@PathVariable long id) {
        return Result.ok(importService.resume(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<BookmarkImportStatusResponse> cancel(@PathVariable long id) {
        return Result.ok(importService.cancel(UserContext.requireUserId(), id));
    }
}
