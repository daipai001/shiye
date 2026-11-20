package com.shiye.user.module.team.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.team.domain.dto.TeamCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamInviteCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamJoinRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberAddRequest;
import com.shiye.user.module.article.domain.vo.ArticleFolderVO;
import com.shiye.user.module.tag.domain.vo.TagVO;
import com.shiye.user.module.team.domain.dto.TeamFolderRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberUpdateRequest;
import com.shiye.user.module.team.domain.dto.TeamTagRequest;
import com.shiye.user.module.team.domain.dto.TeamTransferRequest;
import com.shiye.user.module.team.domain.dto.TeamUpdateRequest;
import com.shiye.user.module.team.domain.vo.TeamDetailVO;
import com.shiye.user.module.team.domain.vo.TeamInviteVO;
import com.shiye.user.module.team.domain.vo.TeamMemberVO;
import com.shiye.user.module.team.domain.vo.TeamVO;
import com.shiye.user.module.team.service.TeamDissolveService;
import com.shiye.user.module.team.service.TeamService;
import com.shiye.user.module.team.service.TeamTaxonomyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamDissolveService dissolveService;
    private final TeamTaxonomyService taxonomyService;

    public TeamController(TeamService teamService,
                          TeamDissolveService dissolveService,
                          TeamTaxonomyService taxonomyService) {
        this.teamService = teamService;
        this.dissolveService = dissolveService;
        this.taxonomyService = taxonomyService;
    }

    @GetMapping
    public Result<List<TeamVO>> list() {
        return Result.ok(teamService.myTeams(UserContext.requireUserId()));
    }

    @PostMapping
    public Result<TeamDetailVO> create(@Valid @RequestBody TeamCreateRequest request) {
        return Result.ok(teamService.create(UserContext.requireUserId(), request));
    }

    @GetMapping("/{id}")
    public Result<TeamDetailVO> detail(@PathVariable long id) {
        return Result.ok(teamService.detail(UserContext.requireUserId(), id));
    }

    @PutMapping("/{id}")
    public Result<TeamVO> update(@PathVariable long id,
                                 @Valid @RequestBody TeamUpdateRequest request) {
        return Result.ok(teamService.update(UserContext.requireUserId(), id, request));
    }

    @GetMapping("/{id}/members")
    public Result<List<TeamMemberVO>> members(@PathVariable long id) {
        return Result.ok(teamService.members(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/members")
    public Result<TeamMemberVO> addMember(@PathVariable long id,
                                          @Valid @RequestBody TeamMemberAddRequest request) {
        return Result.ok(teamService.addMember(UserContext.requireUserId(), id, request));
    }

    @PostMapping("/{id}/invites")
    public Result<TeamInviteVO> createInvite(@PathVariable long id,
                                             @Valid @RequestBody TeamInviteCreateRequest request) {
        return Result.ok(teamService.createInvite(UserContext.requireUserId(), id, request));
    }

    @PostMapping("/join")
    public Result<TeamDetailVO> join(@Valid @RequestBody TeamJoinRequest request) {
        return Result.ok(teamService.join(UserContext.requireUserId(), request));
    }

    @PutMapping("/{id}/members/{userId}")
    public Result<TeamMemberVO> updateMember(@PathVariable long id,
                                             @PathVariable long userId,
                                             @Valid @RequestBody TeamMemberUpdateRequest request) {
        return Result.ok(teamService.updateMember(
                UserContext.requireUserId(), id, userId, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable long id, @PathVariable long userId) {
        teamService.removeMember(UserContext.requireUserId(), id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/transfer")
    public Result<TeamDetailVO> transfer(@PathVariable long id,
                                         @Valid @RequestBody TeamTransferRequest request) {
        return Result.ok(teamService.transferOwner(
                UserContext.requireUserId(), id, request.userId()));
    }

    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable long id) {
        teamService.leave(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> dissolve(@PathVariable long id) {
        dissolveService.dissolve(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @GetMapping("/{id}/tags")
    public Result<List<TagVO>> tags(@PathVariable long id) {
        return Result.ok(taxonomyService.tags(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/tags")
    public Result<TagVO> createTag(@PathVariable long id,
                                   @Valid @RequestBody TeamTagRequest request) {
        return Result.ok(taxonomyService.createTag(
                UserContext.requireUserId(), id, request));
    }

    @PutMapping("/{id}/tags/{tagId}")
    public Result<TagVO> updateTag(@PathVariable long id, @PathVariable long tagId,
                                   @Valid @RequestBody TeamTagRequest request) {
        return Result.ok(taxonomyService.updateTag(
                UserContext.requireUserId(), id, tagId, request));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public Result<Void> deleteTag(@PathVariable long id, @PathVariable long tagId) {
        taxonomyService.deleteTag(UserContext.requireUserId(), id, tagId);
        return Result.ok();
    }

    @GetMapping("/{id}/folders")
    public Result<List<ArticleFolderVO>> folders(@PathVariable long id) {
        return Result.ok(taxonomyService.folders(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/folders")
    public Result<ArticleFolderVO> createFolder(@PathVariable long id,
                                                @Valid @RequestBody TeamFolderRequest request) {
        return Result.ok(taxonomyService.createFolder(
                UserContext.requireUserId(), id, request));
    }

    @PutMapping("/{id}/folders/{folderId}")
    public Result<ArticleFolderVO> updateFolder(@PathVariable long id,
                                                @PathVariable long folderId,
                                                @Valid @RequestBody TeamFolderRequest request) {
        return Result.ok(taxonomyService.updateFolder(
                UserContext.requireUserId(), id, folderId, request));
    }

    @DeleteMapping("/{id}/folders/{folderId}")
    public Result<Void> deleteFolder(@PathVariable long id, @PathVariable long folderId) {
        taxonomyService.deleteFolder(UserContext.requireUserId(), id, folderId);
        return Result.ok();
    }
}
