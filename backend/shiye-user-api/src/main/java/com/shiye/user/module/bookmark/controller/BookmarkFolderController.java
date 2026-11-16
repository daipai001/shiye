package com.shiye.user.module.bookmark.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.bookmark.domain.dto.BookmarkFolderCreateRequest;
import com.shiye.user.module.bookmark.domain.dto.BookmarkFolderUpdateRequest;
import com.shiye.user.module.bookmark.domain.vo.BookmarkFolderListResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkFolderVO;
import com.shiye.user.module.bookmark.service.BookmarkFolderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookmark-folders")
public class BookmarkFolderController {

    private final BookmarkFolderService folderService;

    public BookmarkFolderController(BookmarkFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public Result<BookmarkFolderListResponse> list() {
        return Result.ok(new BookmarkFolderListResponse(
                folderService.list(UserContext.requireUserId())));
    }

    @PostMapping
    public Result<BookmarkFolderVO> create(@Valid @RequestBody BookmarkFolderCreateRequest request) {
        return Result.ok(folderService.create(UserContext.requireUserId(), request));
    }

    @PatchMapping("/{id}")
    public Result<BookmarkFolderVO> update(@PathVariable long id,
                                           @Valid @RequestBody BookmarkFolderUpdateRequest request) {
        return Result.ok(folderService.update(UserContext.requireUserId(), id, request));
    }
}
