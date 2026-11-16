package com.shiye.user.module.bookmark.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import com.shiye.common.security.Hashes;
import com.shiye.user.common.util.UrlNormalizer;
import com.shiye.user.module.bookmark.domain.dto.BookmarkImportCreateRequest;
import com.shiye.user.module.bookmark.domain.entity.BookmarkFolderEntity;
import com.shiye.user.module.bookmark.domain.entity.BookmarkImportEntity;
import com.shiye.user.module.bookmark.domain.entity.BookmarkItemEntity;
import com.shiye.user.module.bookmark.domain.entity.IdempotencyKeyEntity;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportCreateResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportItemVO;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportStatusResponse;
import com.shiye.user.module.bookmark.domain.vo.BookmarkImportSummaryVO;
import com.shiye.user.module.bookmark.mapper.BookmarkImportMapper;
import com.shiye.user.module.bookmark.mapper.BookmarkItemMapper;
import com.shiye.user.module.bookmark.mapper.IdempotencyKeyMapper;
import com.shiye.user.module.bookmark.service.BookmarkFolderService;
import com.shiye.user.module.bookmark.service.BookmarkImportService;
import com.shiye.user.module.bookmark.service.BookmarkRateLimitService;
import com.shiye.user.module.bookmark.support.BookmarkBrowser;
import com.shiye.user.module.bookmark.support.BookmarkImportStatus;
import com.shiye.user.module.bookmark.support.BookmarkItemStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookmarkImportServiceImpl implements BookmarkImportService {

    private static final String IDEMPOTENCY_ENDPOINT = "POST /api/v1/bookmark-imports";
    private static final int MAX_PAGE_SIZE = 100;

    private final BookmarkImportMapper importMapper;
    private final BookmarkItemMapper itemMapper;
    private final BookmarkFolderService folderService;
    private final IdempotencyKeyMapper idempotencyKeyMapper;
    private final BookmarkRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public BookmarkImportServiceImpl(BookmarkImportMapper importMapper,
                                     BookmarkItemMapper itemMapper,
                                     BookmarkFolderService folderService,
                                     IdempotencyKeyMapper idempotencyKeyMapper,
                                     BookmarkRateLimitService rateLimitService,
                                     ObjectMapper objectMapper) {
        this.importMapper = importMapper;
        this.itemMapper = itemMapper;
        this.folderService = folderService;
        this.idempotencyKeyMapper = idempotencyKeyMapper;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public BookmarkImportCreateResponse create(long userId, BookmarkImportCreateRequest request,
                                               String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key == null) {
            return doCreate(userId, request);
        }

        String requestHash = hashRequest(request);
        IdempotencyKeyEntity existing = idempotencyKeyMapper.find(userId, IDEMPOTENCY_ENDPOINT, key);
        if (existing != null) {
            if (existing.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                if (!requestHash.equals(existing.getRequestHash())) {
                    throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "相同幂等键对应不同请求");
                }
                if (existing.getResponseBody() == null) {
                    throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "相同请求正在处理");
                }
                return readResponse(existing.getResponseBody());
            }
            idempotencyKeyMapper.deleteById(existing.getId());
        }

        BookmarkImportCreateResponse response = doCreate(userId, request);
        IdempotencyKeyEntity row = new IdempotencyKeyEntity();
        row.setUserId(userId);
        row.setEndpoint(IDEMPOTENCY_ENDPOINT);
        row.setIdempotencyKey(key);
        row.setRequestHash(requestHash);
        row.setResponseCode(200);
        row.setResponseBody(writeResponse(response));
        row.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        try {
            idempotencyKeyMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "相同请求正在处理");
        }
        return response;
    }

    @Override
    public PageResult<BookmarkImportSummaryVO> list(long userId, int page, int pageSize, String status) {
        int safePage = Math.max(1, page);
        int safeSize = safePageSize(pageSize);
        String statusFilter = status == null || status.isBlank()
                ? null
                : BookmarkImportStatus.parse(status).name();
        long total = importMapper.count(userId, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<BookmarkImportSummaryVO> items = importMapper
                .findPage(userId, statusFilter, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(BookmarkImportSummaryVO::from)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public BookmarkImportSummaryVO detail(long userId, long importId) {
        return BookmarkImportSummaryVO.from(findOwned(userId, importId));
    }

    @Override
    public PageResult<BookmarkImportItemVO> items(long userId, long importId,
                                                  int page, int pageSize, String status) {
        findOwned(userId, importId);
        int safePage = Math.max(1, page);
        int safeSize = safePageSize(pageSize);
        String statusFilter = status == null || status.isBlank()
                ? null
                : BookmarkItemStatus.parse(status).name();
        long total = itemMapper.count(importId, userId, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<BookmarkImportItemVO> items = itemMapper
                .findPage(importId, userId, statusFilter, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(BookmarkImportItemVO::from)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    @Transactional
    public BookmarkImportStatusResponse pause(long userId, long importId) {
        BookmarkImportEntity entity = findOwned(userId, importId);
        BookmarkImportStatus status = BookmarkImportStatus.parse(entity.getStatus());
        if (status == BookmarkImportStatus.PAUSED) {
            return new BookmarkImportStatusResponse(status.name());
        }
        if (status != BookmarkImportStatus.PENDING && status != BookmarkImportStatus.RUNNING) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "当前状态不能暂停");
        }
        importMapper.updateStatus(importId, userId, BookmarkImportStatus.PAUSED.name());
        return new BookmarkImportStatusResponse(BookmarkImportStatus.PAUSED.name());
    }

    @Override
    @Transactional
    public BookmarkImportStatusResponse resume(long userId, long importId) {
        BookmarkImportEntity entity = findOwned(userId, importId);
        BookmarkImportStatus status = BookmarkImportStatus.parse(entity.getStatus());
        if (status == BookmarkImportStatus.RUNNING) {
            return new BookmarkImportStatusResponse(status.name());
        }
        if (status != BookmarkImportStatus.PAUSED) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "只有暂停的任务可以恢复");
        }
        importMapper.markRunning(importId, userId);
        return new BookmarkImportStatusResponse(BookmarkImportStatus.RUNNING.name());
    }

    @Override
    @Transactional
    public BookmarkImportStatusResponse cancel(long userId, long importId) {
        BookmarkImportEntity entity = findOwned(userId, importId);
        BookmarkImportStatus status = BookmarkImportStatus.parse(entity.getStatus());
        if (status == BookmarkImportStatus.CANCELLED) {
            return new BookmarkImportStatusResponse(status.name());
        }
        if (!status.isActive()) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "当前状态不能取消");
        }
        itemMapper.cancelPending(importId, userId);
        importMapper.markFinished(importId, userId, BookmarkImportStatus.CANCELLED.name());
        importMapper.refreshCounts(importId, userId);
        return new BookmarkImportStatusResponse(BookmarkImportStatus.CANCELLED.name());
    }

    @Override
    @Transactional
    public int retry(long userId, long importId, List<Long> itemIds) {
        BookmarkImportEntity entity = findOwned(userId, importId);
        BookmarkImportStatus status = BookmarkImportStatus.parse(entity.getStatus());
        if (status.isActive()) {
            throw new BizException(ErrorCode.IMPORT_ALREADY_RUNNING, "导入任务尚未结束，不能重试");
        }
        if (status != BookmarkImportStatus.DONE && status != BookmarkImportStatus.FAILED) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "当前状态不能重试");
        }
        int retried = itemMapper.retryFailed(importId, userId, itemIds);
        if (retried > 0) {
            importMapper.markPending(importId, userId);
            importMapper.refreshCounts(importId, userId);
        }
        return retried;
    }

    private BookmarkImportCreateResponse doCreate(long userId, BookmarkImportCreateRequest request) {
        BookmarkBrowser browser = BookmarkBrowser.parse(request.folder().browser());
        String browserFolderId = request.folder().folderId().trim();
        String folderPath = request.folder().folderPath().trim();
        if (browserFolderId.isEmpty() || folderPath.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "folderId / folderPath 不能为空");
        }
        boolean recursive = request.folder().recursive() == null || request.folder().recursive();

        Map<String, PreparedItem> prepared = prepareItems(request.items());
        if (prepared.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "没有可导入的有效 URL");
        }

        BookmarkFolderEntity folder = folderService.ensureFolder(
                userId, browser, browserFolderId, folderPath);

        if (request.importId() == null || request.importId().isBlank()) {
            rateLimitService.checkCreate(userId);
            BookmarkImportEntity active = importMapper.findActiveByFolder(userId, folder.getId());
            if (active != null) {
                throw new BizException(ErrorCode.IMPORT_ALREADY_RUNNING, "该文件夹已有进行中的导入任务");
            }

            BookmarkImportEntity entity = new BookmarkImportEntity();
            entity.setUserId(userId);
            entity.setBookmarkFolderId(folder.getId());
            entity.setBrowser(browser.name());
            entity.setFolderId(browserFolderId);
            entity.setFolderPath(folderPath);
            entity.setRecursive(recursive);
            entity.setStatus(BookmarkImportStatus.PENDING.name());
            entity.setTotal(prepared.size());
            importMapper.insert(entity);

            insertItems(entity.getId(), userId, browserFolderId, folderPath, prepared.values());
            importMapper.refreshCounts(entity.getId(), userId);
            BookmarkImportEntity saved = findOwned(userId, entity.getId());
            folderService.markSynced(userId, folder.getId());
            return toCreateResponse(saved);
        }

        long targetId = Long.parseLong(request.importId());
        BookmarkImportEntity target = findOwned(userId, targetId);
        if (!folder.getId().equals(target.getBookmarkFolderId())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "追加的文件夹与导入任务不一致");
        }
        if (!BookmarkImportStatus.PENDING.name().equals(target.getStatus())) {
            throw new BizException(ErrorCode.IMPORT_ALREADY_RUNNING, "导入任务已开始处理，无法继续追加");
        }

        Set<String> existingHashes = new HashSet<>(itemMapper.findUrlHashes(targetId, userId));
        List<PreparedItem> toInsert = prepared.values().stream()
                .filter(item -> !existingHashes.contains(item.normalized().urlHash()))
                .toList();
        if (!toInsert.isEmpty()) {
            insertItems(targetId, userId, browserFolderId, folderPath, toInsert);
            importMapper.refreshCounts(targetId, userId);
        }
        folderService.markSynced(userId, folder.getId());
        BookmarkImportEntity saved = findOwned(userId, targetId);
        return toCreateResponse(saved);
    }

    private Map<String, PreparedItem> prepareItems(List<BookmarkImportCreateRequest.Item> items) {
        Map<String, PreparedItem> unique = new LinkedHashMap<>();
        for (BookmarkImportCreateRequest.Item item : items) {
            UrlNormalizer.Normalized normalized = UrlNormalizer.normalize(item.url());
            String title = item.title() == null || item.title().isBlank() ? null : item.title().trim();
            String parentPath = item.parentPath() == null || item.parentPath().isBlank()
                    ? null
                    : item.parentPath().trim();
            unique.putIfAbsent(normalized.urlHash(),
                    new PreparedItem(normalized, title, parentPath));
        }
        return unique;
    }

    private void insertItems(long importId, long userId, String browserFolderId, String rootPath,
                             Iterable<PreparedItem> items) {
        List<BookmarkItemEntity> rows = new ArrayList<>();
        for (PreparedItem item : items) {
            BookmarkItemEntity row = new BookmarkItemEntity();
            row.setImportId(importId);
            row.setUserId(userId);
            row.setTitle(item.title());
            row.setUrl(item.normalized().originalUrl());
            row.setUrlHash(item.normalized().urlHash());
            row.setBrowserFolderId(browserFolderId);
            row.setFolderPath(item.parentPath() == null ? rootPath : item.parentPath());
            row.setStatus(BookmarkItemStatus.PENDING.name());
            rows.add(row);
        }
        itemMapper.insertBatch(rows);
    }

    private BookmarkImportCreateResponse toCreateResponse(BookmarkImportEntity entity) {
        return new BookmarkImportCreateResponse(
                String.valueOf(entity.getId()),
                entity.getStatus(),
                entity.getTotal() == null ? 0 : entity.getTotal()
        );
    }

    private BookmarkImportEntity findOwned(long userId, long importId) {
        BookmarkImportEntity entity = importMapper.findByIdAndUser(importId, userId);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        return entity;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > 128) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key 最长 128 字符");
        }
        return trimmed;
    }

    private String hashRequest(BookmarkImportCreateRequest request) {
        try {
            return Hashes.sha256Hex(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String writeResponse(BookmarkImportCreateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private BookmarkImportCreateResponse readResponse(String body) {
        try {
            return objectMapper.readValue(body, BookmarkImportCreateResponse.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private record PreparedItem(UrlNormalizer.Normalized normalized,
                                String title,
                                String parentPath) {
    }
}
