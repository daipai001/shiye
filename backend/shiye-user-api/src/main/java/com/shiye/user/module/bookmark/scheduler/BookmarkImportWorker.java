package com.shiye.user.module.bookmark.scheduler;

import com.shiye.user.module.article.service.ArticleIngestService;
import com.shiye.user.module.article.support.IngestException;
import com.shiye.user.module.bookmark.domain.entity.BookmarkImportEntity;
import com.shiye.user.module.bookmark.domain.entity.BookmarkItemEntity;
import com.shiye.user.module.bookmark.mapper.BookmarkImportMapper;
import com.shiye.user.module.bookmark.mapper.BookmarkItemMapper;
import com.shiye.user.module.bookmark.support.BookmarkImportStatus;
import com.shiye.user.module.bookmark.support.BookmarkItemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 书签导入 worker：单实例、单线程消费 PENDING 条目。
 * 抓取/解析/切片/embedding 在多用户之间串行执行，V1 先保证正确性和可恢复性。
 */
@Component
public class BookmarkImportWorker {

    private static final Logger log = LoggerFactory.getLogger(BookmarkImportWorker.class);

    private final BookmarkImportMapper importMapper;
    private final BookmarkItemMapper itemMapper;
    private final ArticleIngestService articleIngestService;
    private final boolean enabled;
    private final int maxItemsPerTick;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BookmarkImportWorker(
            BookmarkImportMapper importMapper,
            BookmarkItemMapper itemMapper,
            ArticleIngestService articleIngestService,
            @Value("${shiye.bookmark.worker-enabled:true}") boolean enabled,
            @Value("${shiye.bookmark.worker-max-items-per-tick:2}") int maxItemsPerTick) {
        this.importMapper = importMapper;
        this.itemMapper = itemMapper;
        this.articleIngestService = articleIngestService;
        this.enabled = enabled;
        this.maxItemsPerTick = Math.max(1, maxItemsPerTick);
    }

    @Scheduled(
            fixedDelayString = "${shiye.bookmark.worker-delay-ms:3000}",
            initialDelayString = "${shiye.bookmark.worker-initial-delay-ms:5000}"
    )
    public void tick() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            itemMapper.resetStaleProcessing();
            for (int i = 0; i < maxItemsPerTick; i++) {
                BookmarkImportEntity importEntity = importMapper.findRunnableImport();
                if (importEntity == null) {
                    break;
                }
                if (BookmarkImportStatus.PENDING.name().equals(importEntity.getStatus())) {
                    importMapper.markRunning(importEntity.getId(), importEntity.getUserId());
                }
                BookmarkItemEntity item = itemMapper.findNextPending(
                        importEntity.getId(), importEntity.getUserId());
                if (item == null) {
                    finishIfComplete(importEntity.getId(), importEntity.getUserId());
                    continue;
                }
                processItem(importEntity, item);
            }
        } catch (RuntimeException ex) {
            log.error("bookmark worker tick failed: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }

    private void processItem(BookmarkImportEntity importEntity, BookmarkItemEntity item) {
        BookmarkImportEntity latest = importMapper.findByIdAndUser(
                importEntity.getId(), importEntity.getUserId());
        if (latest == null) {
            return;
        }
        BookmarkImportStatus importStatus = BookmarkImportStatus.parse(latest.getStatus());
        if (importStatus == BookmarkImportStatus.PAUSED
                || importStatus == BookmarkImportStatus.CANCELLED) {
            return;
        }

        itemMapper.updateStatus(item.getId(), item.getUserId(), BookmarkItemStatus.FETCHING.name());
        try {
            ArticleIngestService.IngestOutcome outcome = articleIngestService.ingestBookmark(
                    item.getUserId(),
                    latest.getBookmarkFolderId(),
                    item.getFolderPath(),
                    item.getUrl(),
                    item.getTitle()
            );
            if (outcome.duplicate()) {
                itemMapper.markSkipped(
                        item.getId(),
                        item.getUserId(),
                        outcome.articleId(),
                        "DUPLICATE_URL",
                        "已存在相同 URL 的资料"
                );
            } else {
                itemMapper.markReady(item.getId(), item.getUserId(), outcome.articleId());
            }
        } catch (IngestException ex) {
            itemMapper.markFailed(
                    item.getId(),
                    item.getUserId(),
                    ex.getCode(),
                    truncate(ex.getMessage(), 1000)
            );
            log.info("bookmark item {} failed: {} {}", item.getId(), ex.getCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            itemMapper.markFailed(
                    item.getId(),
                    item.getUserId(),
                    "INGEST_FAILED",
                    "处理失败，请稍后重试"
            );
            log.warn("bookmark item {} crashed: {}", item.getId(), ex.getMessage(), ex);
        } finally {
            importMapper.refreshCounts(importEntity.getId(), importEntity.getUserId());
            finishIfComplete(importEntity.getId(), importEntity.getUserId());
        }
    }

    private void finishIfComplete(long importId, long userId) {
        long pending = itemMapper.countPending(importId, userId);
        if (pending > 0) {
            return;
        }
        BookmarkImportEntity latest = importMapper.findByIdAndUser(importId, userId);
        if (latest == null) {
            return;
        }
        BookmarkImportStatus status = BookmarkImportStatus.parse(latest.getStatus());
        if (status == BookmarkImportStatus.PAUSED
                || status == BookmarkImportStatus.CANCELLED
                || status == BookmarkImportStatus.DONE
                || status == BookmarkImportStatus.FAILED) {
            return;
        }
        importMapper.markFinished(importId, userId, BookmarkImportStatus.DONE.name());
        importMapper.refreshCounts(importId, userId);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
