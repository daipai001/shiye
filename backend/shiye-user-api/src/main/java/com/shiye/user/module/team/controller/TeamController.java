package com.shiye.user.module.team.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.team.domain.dto.TeamCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamInviteCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamJoinRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberAddRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberUpdateRequest;
import com.shiye.user.module.team.domain.dto.TeamUpdateRequest;
import com.shiye.user.module.team.domain.vo.TeamDetailVO;
import com.shiye.user.module.team.domain.vo.TeamInviteVO;
import com.shiye.user.module.team.domain.vo.TeamMemberVO;
import com.shiye.user.module.team.domain.vo.TeamVO;
import com.shiye.user.module.team.service.TeamService;
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

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
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

    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable long id) {
        teamService.leave(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
