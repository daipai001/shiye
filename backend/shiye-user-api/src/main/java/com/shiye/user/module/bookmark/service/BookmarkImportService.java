package com.shiye.user.module.bookmark.service;

import com.shiye.common.api.PageResult;
import com.shiye.user.module.bookmark.domain.dto.BookmarkImportCreateRequest;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportCreateResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportItemVO;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportStatusResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportSummaryVO;

import java.util.List;

public interface BookmarkImportService {

    BookmarkImportCreateResponse create(long userId, BookmarkImportCreateRequest request, String idempotencyKey);

    PageResult<BookmarkImportSummaryVO> list(long userId, int page, int pageSize, String status);

    BookmarkImportSummaryVO detail(long userId, long importId);

    PageResult<BookmarkImportItemVO> items(long userId, long importId, int page, int pageSize, String status);

    BookmarkImportStatusResponse pause(long userId, long importId);

    BookmarkImportStatusResponse resume(long userId, long importId);

    BookmarkImportStatusResponse cancel(long userId, long importId);

    int retry(long userId, long importId, List<Long> itemIds);
}
