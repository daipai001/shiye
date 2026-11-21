package com.shiye.user.module.team.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.article.domain.vo.ArticleFolderVO;
import com.shiye.user.module.article.service.ArticleTeamAccessService;
import com.shiye.user.module.tag.domain.vo.TagVO;
import com.shiye.user.module.team.domain.dto.ArticleTeamFolderRequest;
import com.shiye.user.module.team.domain.dto.ArticleTeamTagRequest;
import com.shiye.user.module.team.domain.dto.TeamFolderRequest;
import com.shiye.user.module.team.domain.dto.TeamTagRequest;
import com.shiye.user.module.team.domain.entity.TeamFolderEntity;
import com.shiye.user.module.team.domain.entity.TeamFolderRow;
import com.shiye.user.module.team.domain.entity.TeamTagEntity;
import com.shiye.user.module.team.domain.entity.TeamTagRow;
import com.shiye.user.module.team.domain.vo.TeamFolderOptionVO;
import com.shiye.user.module.team.domain.vo.TeamTagOptionVO;
import com.shiye.user.module.team.domain.vo.TeamTaxonomyOptionsVO;
import com.shiye.user.module.team.domain.vo.TeamVO;
import com.shiye.user.module.team.mapper.TeamFolderMapper;
import com.shiye.user.module.team.mapper.TeamTagMapper;
import com.shiye.user.module.team.service.TeamService;
import com.shiye.user.module.team.service.TeamTaxonomyService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TeamTaxonomyServiceImpl implements TeamTaxonomyService {

    private static final int MAX_NAME_CHARS = 64;
    private static final String[] COLORS = {
            "#3b82f6", "#10b981", "#f59e0b", "#8b5cf6", "#ef4444", "#06b6d4"
    };

    private final TeamTagMapper tagMapper;
    private final TeamFolderMapper folderMapper;
    private final TeamService teamService;
    private final ArticleTeamAccessService articleAccessService;

    public TeamTaxonomyServiceImpl(TeamTagMapper tagMapper,
                                   TeamFolderMapper folderMapper,
                                   TeamService teamService,
                                   ArticleTeamAccessService articleAccessService) {
        this.tagMapper = tagMapper;
        this.folderMapper = folderMapper;
        this.teamService = teamService;
        this.articleAccessService = articleAccessService;
    }

    @Override
    public List<TagVO> tags(long userId, long teamId) {
        teamService.requireMember(userId, teamId);
        return tagMapper.listWithCounts(teamId).stream()
                .map(row -> new TagVO(
                        String.valueOf(row.getTagId()),
                        row.getName(),
                        row.getColor(),
                        row.getArticleCount() == null ? 0 : row.getArticleCount()))
                .toList();
    }

    @Override
    @Transactional
    public TagVO createTag(long userId, long teamId, TeamTagRequest request) {
        teamService.requireMember(userId, teamId);
        return toVO(createTagInternal(userId, teamId, request.name(), request.color()));
    }

    @Override
    @Transactional
    public TagVO updateTag(long userId, long teamId, long tagId, TeamTagRequest request) {
        teamService.requireMember(userId, teamId);
        TeamTagEntity tag = tagMapper.findByIdAndTeam(tagId, teamId);
        if (tag == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队标签不存在");
        }
        String name = normalizeTagName(request.name());
        if (name == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "标签名不能为空");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        TeamTagEntity sameName = tagMapper.findByNormalizedName(teamId, normalized);
        if (sameName != null && !sameName.getId().equals(tagId)) {
            throw new BizException(ErrorCode.CONFLICT, "标签名已存在");
        }
        String color = request.color() == null || request.color().isBlank()
                ? tag.getColor() : request.color();
        tagMapper.update(tagId, teamId, name, normalized, color);
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setColor(color);
        return toVO(tag);
    }

    @Override
    @Transactional
    public void deleteTag(long userId, long teamId, long tagId) {
        teamService.requireMember(userId, teamId);
        if (tagMapper.findByIdAndTeam(tagId, teamId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队标签不存在");
        }
        tagMapper.deleteArticleTags(tagId, teamId);
        tagMapper.delete(tagId, teamId);
    }

    @Override
    @Transactional
    public TagVO addArticleTag(long userId, long articleId, ArticleTeamTagRequest request) {
        ArticleEntity article = articleAccessService.requireTeamArticle(userId, articleId);
        long teamId = article.getTeamId();
        TeamTagEntity tag;
        if (request.tagId() != null && !request.tagId().isBlank()) {
            tag = tagMapper.findByIdAndTeam(Long.parseLong(request.tagId()), teamId);
            if (tag == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "团队标签不存在");
            }
        } else {
            String name = normalizeTagName(request.name());
            if (name == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "tagId 或 name 至少提供一个");
            }
            tag = createTagInternal(userId, teamId, name, request.color());
        }
        tagMapper.insertArticleTag(articleId, teamId, tag.getId(), userId);
        return toVO(tag);
    }

    @Override
    @Transactional
    public void removeArticleTag(long userId, long articleId, long tagId) {
        ArticleEntity article = articleAccessService.requireTeamArticle(userId, articleId);
        tagMapper.deleteArticleTag(articleId, tagId, article.getTeamId());
    }

    @Override
    public List<ArticleFolderVO> folders(long userId, long teamId) {
        teamService.requireMember(userId, teamId);
        return folderMapper.listWithCounts(teamId).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public ArticleFolderVO createFolder(long userId, long teamId, TeamFolderRequest request) {
        teamService.requireMember(userId, teamId);
        return toVO(createFolderInternal(userId, teamId, request.name()));
    }

    @Override
    @Transactional
    public ArticleFolderVO updateFolder(long userId, long teamId, long folderId,
                                        TeamFolderRequest request) {
        teamService.requireMember(userId, teamId);
        TeamFolderEntity folder = folderMapper.findByIdAndTeam(folderId, teamId);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队文件夹不存在");
        }
        String name = normalizeName(request.name());
        if (name == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "文件夹名不能为空");
        }
        TeamFolderEntity sameName = folderMapper.findByName(teamId, name);
        if (sameName != null && !sameName.getId().equals(folderId)) {
            throw new BizException(ErrorCode.CONFLICT, "文件夹名已存在");
        }
        folderMapper.update(folderId, teamId, name);
        folder.setName(name);
        return toVO(folder);
    }

    @Override
    @Transactional
    public void deleteFolder(long userId, long teamId, long folderId) {
        teamService.requireMember(userId, teamId);
        if (folderMapper.findByIdAndTeam(folderId, teamId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队文件夹不存在");
        }
        folderMapper.deleteArticleFolders(folderId, teamId);
        folderMapper.delete(folderId, teamId);
    }

    @Override
    @Transactional
    public ArticleFolderVO addArticleFolder(long userId, long articleId,
                                            ArticleTeamFolderRequest request) {
        ArticleEntity article = articleAccessService.requireTeamArticle(userId, articleId);
        long teamId = article.getTeamId();
        TeamFolderEntity folder;
        if (request.folderId() != null && !request.folderId().isBlank()) {
            folder = folderMapper.findByIdAndTeam(Long.parseLong(request.folderId()), teamId);
            if (folder == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "团队文件夹不存在");
            }
        } else {
            String name = normalizeName(request.name());
            if (name == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "folderId 或 name 至少提供一个");
            }
            folder = createFolderInternal(userId, teamId, name);
        }
        folderMapper.insertArticleFolder(articleId, teamId, folder.getId(), userId);
        return toVO(folder);
    }

    @Override
    @Transactional
    public void removeArticleFolder(long userId, long articleId, long folderId) {
        ArticleEntity article = articleAccessService.requireTeamArticle(userId, articleId);
        folderMapper.deleteArticleFolder(articleId, folderId, article.getTeamId());
    }

    @Override
    public Map<Long, List<TagVO>> tagsByArticleIds(long userId, long teamId,
                                                   List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        teamService.requireMember(userId, teamId);
        Map<Long, List<TagVO>> grouped = new LinkedHashMap<>();
        for (TeamTagRow row : tagMapper.findByArticleIds(teamId, articleIds)) {
            grouped.computeIfAbsent(row.getArticleId(), key -> new ArrayList<>())
                    .add(new TagVO(
                            String.valueOf(row.getTagId()),
                            row.getName(),
                            row.getColor(),
                            0));
        }
        return grouped;
    }

    @Override
    public Map<Long, List<ArticleFolderVO>> foldersByArticleIds(long userId, long teamId,
                                                                List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        teamService.requireMember(userId, teamId);
        Map<Long, List<ArticleFolderVO>> grouped = new LinkedHashMap<>();
        for (TeamFolderRow row : folderMapper.findByArticleIds(teamId, articleIds)) {
            grouped.computeIfAbsent(row.getArticleId(), key -> new ArrayList<>())
                    .add(new ArticleFolderVO(
                            String.valueOf(row.getFolderId()),
                            row.getName()));
        }
        return grouped;
    }

    @Override
    public List<Long> findArticleIdsByTag(long userId, long teamId, long tagId) {
        teamService.requireMember(userId, teamId);
        return tagMapper.findArticleIdsByTag(teamId, tagId);
    }

    @Override
    public List<Long> findArticleIdsByFolder(long userId, long teamId, long folderId) {
        teamService.requireMember(userId, teamId);
        return folderMapper.findArticleIdsByFolder(teamId, folderId);
    }

    @Override
    @Transactional
    public void clearArticleTaxonomy(long teamId, long articleId) {
        tagMapper.deleteArticleRelations(articleId, teamId);
        folderMapper.deleteArticleRelations(articleId, teamId);
    }

    @Override
    @Transactional
    public void deleteTeamTaxonomy(long teamId) {
        tagMapper.deleteAllArticleRelations(teamId);
        tagMapper.deleteAll(teamId);
        folderMapper.deleteAllArticleRelations(teamId);
        folderMapper.deleteAll(teamId);
    }

    @Override
    public TeamTaxonomyOptionsVO options(long userId) {
        List<TeamTagOptionVO> tags = new ArrayList<>();
        List<TeamFolderOptionVO> folders = new ArrayList<>();
        for (TeamVO team : teamService.myTeams(userId)) {
            long teamId = Long.parseLong(team.id());
            for (TagVO tag : tags(userId, teamId)) {
                tags.add(new TeamTagOptionVO(
                        tag.id(), tag.name(), tag.color(), team.id(), team.name()));
            }
            for (ArticleFolderVO folder : folders(userId, teamId)) {
                folders.add(new TeamFolderOptionVO(
                        folder.id(), folder.path(), team.id(), team.name()));
            }
        }
        return new TeamTaxonomyOptionsVO(tags, folders);
    }

    @Override
    public long teamIdForTag(long tagId) {
        Long teamId = tagMapper.findTeamId(tagId);
        if (teamId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队标签不存在");
        }
        return teamId;
    }

    @Override
    public long teamIdForFolder(long folderId) {
        Long teamId = folderMapper.findTeamId(folderId);
        if (teamId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队文件夹不存在");
        }
        return teamId;
    }

    private TeamTagEntity createTagInternal(long userId, long teamId, String rawName,
                                            String color) {
        String name = normalizeTagName(rawName);
        if (name == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "标签名不能为空");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        TeamTagEntity existing = tagMapper.findByNormalizedName(teamId, normalized);
        if (existing != null) {
            return existing;
        }
        TeamTagEntity tag = new TeamTagEntity();
        tag.setTeamId(teamId);
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setColor(color == null || !color.matches("^#[0-9a-fA-F]{6}$")
                ? COLORS[Math.floorMod(normalized.hashCode(), COLORS.length)]
                : color);
        tag.setCreatedBy(userId);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException ex) {
            TeamTagEntity saved = tagMapper.findByNormalizedName(teamId, normalized);
            if (saved == null) {
                throw new BizException(ErrorCode.CONFLICT, "标签创建失败");
            }
            return saved;
        }
    }

    private TeamFolderEntity createFolderInternal(long userId, long teamId, String rawName) {
        String name = normalizeName(rawName);
        if (name == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "文件夹名不能为空");
        }
        TeamFolderEntity existing = folderMapper.findByName(teamId, name);
        if (existing != null) {
            return existing;
        }
        TeamFolderEntity folder = new TeamFolderEntity();
        folder.setTeamId(teamId);
        folder.setName(name);
        folder.setCreatedBy(userId);
        try {
            folderMapper.insert(folder);
            return folder;
        } catch (DuplicateKeyException ex) {
            TeamFolderEntity saved = folderMapper.findByName(teamId, name);
            if (saved == null) {
                throw new BizException(ErrorCode.CONFLICT, "文件夹创建失败");
            }
            return saved;
        }
    }

    private TagVO toVO(TeamTagEntity tag) {
        return new TagVO(String.valueOf(tag.getId()), tag.getName(), tag.getColor(), 0);
    }

    private ArticleFolderVO toVO(TeamFolderEntity folder) {
        return new ArticleFolderVO(
                String.valueOf(folder.getId()),
                folder.getName(),
                folder.getArticleCount() == null ? 0 : folder.getArticleCount());
    }

    private String normalizeTagName(String raw) {
        String name = normalizeName(raw);
        if (name == null) {
            return null;
        }
        while (name.startsWith("#")) {
            name = name.substring(1).trim();
        }
        return name.isEmpty() ? null : name;
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            return null;
        }
        return name.length() <= MAX_NAME_CHARS ? name : name.substring(0, MAX_NAME_CHARS);
    }
}
