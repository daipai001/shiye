package com.shiye.user.module.bookmark.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.bookmark.domain.dto.BookmarkFolderCreateRequest;
import com.shiye.user.module.bookmark.domain.dto.BookmarkFolderUpdateRequest;
import com.shiye.user.module.bookmark.domain.entity.BookmarkFolderEntity;
import com.shiye.user.module.bookmark.domain.vo.BookmarkFolderVO;
import com.shiye.user.module.bookmark.mapper.BookmarkFolderMapper;
import com.shiye.user.module.bookmark.service.BookmarkFolderService;
import com.shiye.user.module.bookmark.support.BookmarkBrowser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookmarkFolderServiceImpl implements BookmarkFolderService {

    private final BookmarkFolderMapper folderMapper;

    public BookmarkFolderServiceImpl(BookmarkFolderMapper folderMapper) {
        this.folderMapper = folderMapper;
    }

    @Override
    public List<BookmarkFolderVO> list(long userId) {
        return folderMapper.findByUserId(userId).stream()
                .map(BookmarkFolderVO::from)
                .toList();
    }

    @Override
    @Transactional
    public BookmarkFolderVO create(long userId, BookmarkFolderCreateRequest request) {
        BookmarkBrowser browser = BookmarkBrowser.parse(request.browser());
        String browserFolderId = request.folderId().trim();
        String folderPath = request.folderPath().trim();
        if (folderPath.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "folderPath 不能为空");
        }
        if (folderMapper.findByBrowserFolder(userId, browser.name(), browserFolderId) != null) {
            throw new BizException(ErrorCode.CONFLICT, "该浏览器文件夹已添加");
        }

        boolean primary = Boolean.TRUE.equals(request.isPrimary());
        boolean autoSync = request.autoSync() == null || request.autoSync();
        if (primary) {
            folderMapper.clearPrimary(userId);
        }

        BookmarkFolderEntity entity = new BookmarkFolderEntity();
        entity.setUserId(userId);
        entity.setBrowser(browser.name());
        entity.setFolderId(browserFolderId);
        entity.setFolderPath(folderPath);
        entity.setIsPrimary(primary);
        entity.setAutoSync(autoSync);
        try {
            folderMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.CONFLICT, "该浏览器文件夹已添加");
        }
        return BookmarkFolderVO.from(findOwned(userId, entity.getId()));
    }

    @Override
    @Transactional
    public BookmarkFolderVO update(long userId, long folderId, BookmarkFolderUpdateRequest request) {
        BookmarkFolderEntity current = findOwned(userId, folderId);
        boolean primary = request.isPrimary() == null
                ? Boolean.TRUE.equals(current.getIsPrimary())
                : request.isPrimary();
        boolean autoSync = request.autoSync() == null
                ? Boolean.TRUE.equals(current.getAutoSync())
                : request.autoSync();

        if (primary && !Boolean.TRUE.equals(current.getIsPrimary())) {
            folderMapper.clearPrimary(userId);
        }
        folderMapper.updateSettings(folderId, userId, primary, autoSync);
        return BookmarkFolderVO.from(findOwned(userId, folderId));
    }

    @Override
    @Transactional
    public BookmarkFolderEntity ensureFolder(long userId, BookmarkBrowser browser,
                                             String browserFolderId, String folderPath) {
        BookmarkFolderEntity existing = folderMapper.findByBrowserFolder(
                userId, browser.name(), browserFolderId);
        if (existing != null) {
            if (!folderPath.equals(existing.getFolderPath())) {
                folderMapper.updatePath(existing.getId(), userId, folderPath);
                existing.setFolderPath(folderPath);
            }
            return existing;
        }

        BookmarkFolderEntity entity = new BookmarkFolderEntity();
        entity.setUserId(userId);
        entity.setBrowser(browser.name());
        entity.setFolderId(browserFolderId);
        entity.setFolderPath(folderPath);
        entity.setIsPrimary(false);
        entity.setAutoSync(true);
        try {
            folderMapper.insert(entity);
            return entity;
        } catch (DuplicateKeyException ex) {
            BookmarkFolderEntity raced = folderMapper.findByBrowserFolder(
                    userId, browser.name(), browserFolderId);
            if (raced == null) {
                throw ex;
            }
            return raced;
        }
    }

    @Override
    public void markSynced(long userId, long folderId) {
        folderMapper.markSynced(folderId, userId);
    }

    private BookmarkFolderEntity findOwned(long userId, long folderId) {
        BookmarkFolderEntity entity = folderMapper.findByIdAndUser(folderId, userId);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "书签文件夹不存在");
        }
        return entity;
    }
}
