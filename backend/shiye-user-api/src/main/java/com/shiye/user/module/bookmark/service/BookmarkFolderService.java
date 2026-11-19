package com.shiye.user.module.bookmark.service;

import com.shiye.user.module.bookmark.domain.dto.BookmarkFolderCreateRequest;
import com.shiye.user.module.bookmark.domain.dto.BookmarkFolderUpdateRequest;
import com.shiye.user.module.bookmark.domain.entity.BookmarkFolderEntity;
import com.shiye.user.module.bookmark.domain.vo.BookmarkFolderVO;
import com.shiye.user.module.bookmark.support.BookmarkBrowser;

import java.util.List;

public interface BookmarkFolderService {

    List<BookmarkFolderVO> list(long userId);

    BookmarkFolderVO create(long userId, BookmarkFolderCreateRequest request);

    BookmarkFolderVO update(long userId, long folderId, BookmarkFolderUpdateRequest request);

    BookmarkFolderEntity ensureFolder(long userId, BookmarkBrowser browser, String browserFolderId, String folderPath);

    boolean existsForUser(long userId, long folderId);

    void markSynced(long userId, long folderId);
}
