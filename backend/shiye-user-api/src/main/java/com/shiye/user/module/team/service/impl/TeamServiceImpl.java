package com.shiye.user.module.team.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.auth.domain.vo.UserSummary;
import com.shiye.user.module.auth.service.AuthService;
import com.shiye.common.security.Hashes;
import com.shiye.common.security.SecureCodes;
import com.shiye.user.module.team.domain.dto.TeamCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamInviteCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamJoinRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberAddRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberUpdateRequest;
import com.shiye.user.module.team.domain.dto.TeamUpdateRequest;
import com.shiye.user.module.team.domain.entity.TeamEntity;
import com.shiye.user.module.team.domain.entity.TeamInviteEntity;
import com.shiye.user.module.team.domain.entity.TeamMemberEntity;
import com.shiye.user.module.team.domain.vo.TeamDetailVO;
import com.shiye.user.module.team.domain.vo.TeamInviteVO;
import com.shiye.user.module.team.domain.vo.TeamMemberVO;
import com.shiye.user.module.team.domain.vo.TeamQuota;
import com.shiye.user.module.team.domain.vo.TeamVO;
import com.shiye.user.module.team.mapper.TeamInviteMapper;
import com.shiye.user.module.team.mapper.TeamMapper;
import com.shiye.user.module.team.mapper.TeamMemberMapper;
import com.shiye.user.module.team.service.TeamService;
import com.shiye.user.module.team.support.TeamAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeamServiceImpl implements TeamService {

    private static final long DEFAULT_TOKEN_QUOTA = 1_000_000L;
    private static final BigDecimal DEFAULT_COST_QUOTA = new BigDecimal("20.000000");
    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int INVITE_EXPIRE_DAYS = 7;

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamInviteMapper inviteMapper;
    private final AuthService authService;

    public TeamServiceImpl(TeamMapper teamMapper,
                           TeamMemberMapper memberMapper,
                           TeamInviteMapper inviteMapper,
                           AuthService authService) {
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.inviteMapper = inviteMapper;
        this.authService = authService;
    }

    @Override
    public List<TeamVO> myTeams(long userId) {
        return teamMapper.findByUser(userId).stream()
                .map(TeamVO::from)
                .toList();
    }

    @Override
    @Transactional
    public TeamDetailVO create(long userId, TeamCreateRequest request) {
        TeamEntity team = new TeamEntity();
        team.setName(request.name().trim());
        team.setOwnerUserId(userId);
        team.setStatus(STATUS_ACTIVE);
        team.setMonthlyTokenQuota(DEFAULT_TOKEN_QUOTA);
        team.setMonthlyCostQuota(DEFAULT_COST_QUOTA);
        teamMapper.insert(team);

        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole(ROLE_OWNER);
        member.setStatus(STATUS_ACTIVE);
        memberMapper.insert(member);

        return detail(userId, team.getId());
    }

    @Override
    public TeamDetailVO detail(long userId, long teamId) {
        TeamAccess access = requireMember(userId, teamId);
        List<TeamMemberVO> members = members(userId, teamId);
        return TeamDetailVO.from(TeamVO.from(access.team()), members);
    }

    @Override
    @Transactional
    public TeamVO update(long userId, long teamId, TeamUpdateRequest request) {
        TeamAccess access = requireOwner(userId, teamId);
        teamMapper.update(teamId, request.name().trim(),
                request.monthlyTokenQuota(), request.monthlyCostQuota());
        TeamEntity updated = teamMapper.findById(teamId);
        if (updated == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队不存在");
        }
        updated.setMyRole(access.role());
        return TeamVO.from(updated);
    }

    @Override
    public List<TeamMemberVO> members(long userId, long teamId) {
        requireMember(userId, teamId);
        return memberMapper.findByTeam(teamId).stream()
                .map(TeamMemberVO::from)
                .toList();
    }

    @Override
    @Transactional
    public TeamMemberVO addMember(long userId, long teamId, TeamMemberAddRequest request) {
        TeamAccess access = requireManager(userId, teamId);
        String role = normalizeRole(request.role());
        if (ROLE_ADMIN.equals(role) && !access.isOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有团队所有者可以添加管理员");
        }
        UserSummary user = authService.findByEmail(request.email());
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该邮箱还没有注册拾页");
        }
        if (!STATUS_ACTIVE.equals(user.status())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "该用户已被禁用");
        }
        long targetUserId = Long.parseLong(user.id());
        if (memberMapper.findActive(teamId, targetUserId) != null) {
            throw new BizException(ErrorCode.CONFLICT, "该用户已在团队中");
        }
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(teamId);
        member.setUserId(targetUserId);
        member.setRole(role);
        member.setStatus(STATUS_ACTIVE);
        memberMapper.insert(member);

        TeamMemberEntity saved = memberMapper.findActive(teamId, targetUserId);
        if (saved == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        return TeamMemberVO.from(saved);
    }

    @Override
    @Transactional
    public TeamInviteVO createInvite(long userId, long teamId, TeamInviteCreateRequest request) {
        TeamAccess access = requireManager(userId, teamId);
        String role = normalizeRole(request.role());
        if (ROLE_ADMIN.equals(role) && !access.isOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有团队所有者可以邀请管理员");
        }
        String code = SecureCodes.code(12);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusDays(INVITE_EXPIRE_DAYS);
        TeamInviteEntity invite = new TeamInviteEntity();
        invite.setTeamId(teamId);
        invite.setInviterUserId(userId);
        invite.setTokenHash(Hashes.sha256Hex(code));
        invite.setRole(role);
        invite.setExpiresAt(expiresAt);
        inviteMapper.insert(invite);
        return new TeamInviteVO(code, role, expiresAt);
    }

    @Override
    @Transactional
    public TeamDetailVO join(long userId, TeamJoinRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        TeamInviteEntity invite = inviteMapper.findActiveByHash(Hashes.sha256Hex(code));
        if (invite == null) {
            throw new BizException(ErrorCode.INVALID_AUTH_CODE, "邀请码无效或已过期");
        }
        TeamEntity team = teamMapper.findById(invite.getTeamId());
        if (team == null || !STATUS_ACTIVE.equals(team.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队不存在");
        }
        if (memberMapper.findActive(invite.getTeamId(), userId) != null) {
            throw new BizException(ErrorCode.CONFLICT, "你已经是团队成员");
        }
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(invite.getTeamId());
        member.setUserId(userId);
        member.setRole(invite.getRole());
        member.setStatus(STATUS_ACTIVE);
        memberMapper.insert(member);
        if (inviteMapper.markUsed(invite.getId()) == 0) {
            throw new BizException(ErrorCode.INVALID_AUTH_CODE, "邀请码已被使用");
        }
        return detail(userId, invite.getTeamId());
    }

    @Override
    @Transactional
    public TeamMemberVO updateMember(long userId, long teamId, long targetUserId,
                                     TeamMemberUpdateRequest request) {
        TeamAccess access = requireManager(userId, teamId);
        TeamMemberEntity target = requireActiveMember(teamId, targetUserId);
        if (ROLE_OWNER.equals(target.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能修改团队所有者");
        }
        if (targetUserId == userId) {
            throw new BizException(ErrorCode.CONFLICT, "不能修改自己的角色");
        }
        String role = normalizeRole(request.role());
        if (!access.isOwner()
                && (ROLE_ADMIN.equals(target.getRole()) || ROLE_ADMIN.equals(role))) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有团队所有者可以管理管理员");
        }
        memberMapper.updateRole(teamId, targetUserId, role);
        target.setRole(role);
        return TeamMemberVO.from(target);
    }

    @Override
    @Transactional
    public void removeMember(long userId, long teamId, long targetUserId) {
        TeamAccess access = requireManager(userId, teamId);
        TeamMemberEntity target = requireActiveMember(teamId, targetUserId);
        if (ROLE_OWNER.equals(target.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能移除团队所有者");
        }
        if (targetUserId == userId) {
            throw new BizException(ErrorCode.CONFLICT, "请使用退出团队");
        }
        if (!access.isOwner() && ROLE_ADMIN.equals(target.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有团队所有者可以移除管理员");
        }
        memberMapper.delete(teamId, targetUserId);
    }

    @Override
    @Transactional
    public TeamDetailVO transferOwner(long userId, long teamId, long targetUserId) {
        requireOwner(userId, teamId);
        if (targetUserId == userId) {
            throw new BizException(ErrorCode.CONFLICT, "不能转让给自己");
        }
        TeamMemberEntity target = requireActiveMember(teamId, targetUserId);
        if (ROLE_OWNER.equals(target.getRole())) {
            throw new BizException(ErrorCode.CONFLICT, "该成员已经是所有者");
        }
        teamMapper.updateOwner(teamId, targetUserId);
        memberMapper.updateRole(teamId, targetUserId, ROLE_OWNER);
        memberMapper.updateRole(teamId, userId, ROLE_ADMIN);
        return detail(userId, teamId);
    }

    @Override
    @Transactional
    public void leave(long userId, long teamId) {
        TeamMemberEntity member = requireActiveMember(teamId, userId);
        if (ROLE_OWNER.equals(member.getRole())) {
            throw new BizException(ErrorCode.CONFLICT, "团队所有者不能退出团队");
        }
        memberMapper.delete(teamId, userId);
    }

    @Override
    public TeamAccess requireMember(long userId, long teamId) {
        TeamEntity team = teamMapper.findById(teamId);
        if (team == null || !STATUS_ACTIVE.equals(team.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队不存在");
        }
        TeamMemberEntity member = memberMapper.findActive(teamId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "不是团队成员");
        }
        team.setMyRole(member.getRole());
        return new TeamAccess(team, member.getRole());
    }

    @Override
    public TeamAccess requireManager(long userId, long teamId) {
        TeamAccess access = requireMember(userId, teamId);
        if (!access.isManager()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要团队管理员权限");
        }
        return access;
    }

    @Override
    public TeamAccess requireOwner(long userId, long teamId) {
        TeamAccess access = requireMember(userId, teamId);
        if (!access.isOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要团队所有者权限");
        }
        return access;
    }

    @Override
    public Set<Long> teamIdsForUser(long userId) {
        return teamMapper.findByUser(userId).stream()
                .map(TeamEntity::getId)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isMember(long userId, long teamId) {
        return memberMapper.findActive(teamId, userId) != null;
    }

    @Override
    public TeamQuota quota(long teamId) {
        TeamEntity team = teamMapper.findById(teamId);
        if (team == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队不存在");
        }
        return new TeamQuota(
                team.getMonthlyTokenQuota() == null ? 0 : team.getMonthlyTokenQuota(),
                team.getMonthlyCostQuota() == null ? BigDecimal.ZERO : team.getMonthlyCostQuota()
        );
    }

    @Override
    public long countMembers(long teamId) {
        return memberMapper.countActive(teamId);
    }

    private TeamMemberEntity requireActiveMember(long teamId, long userId) {
        TeamMemberEntity member = memberMapper.findActive(teamId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队成员不存在");
        }
        return member;
    }

    private String normalizeRole(String role) {
        String value = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_ADMIN.equals(value) && !ROLE_MEMBER.equals(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "role 仅支持 ADMIN / MEMBER");
        }
        return value;
    }
}
