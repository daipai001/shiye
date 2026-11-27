package com.shiye.user.module.team.service;

import com.shiye.user.module.article.service.ArticleQueryService;
import com.shiye.user.module.team.mapper.TeamInviteMapper;
import com.shiye.user.module.team.mapper.TeamMapper;
import com.shiye.user.module.team.mapper.TeamMemberMapper;
import com.shiye.user.module.team.support.TeamAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 解散团队：团队资料回到各自所有者的个人库，成员和邀请码清理，团队软删除。
 */
@Service
public class TeamDissolveService {

    private final TeamService teamService;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamInviteMapper inviteMapper;
    private final ArticleQueryService articleQueryService;
    private final TeamTaxonomyService taxonomyService;
    private final TeamAuditService auditService;

    public TeamDissolveService(TeamService teamService,
                               TeamMapper teamMapper,
                               TeamMemberMapper memberMapper,
                               TeamInviteMapper inviteMapper,
                               ArticleQueryService articleQueryService,
                               TeamTaxonomyService taxonomyService,
                               TeamAuditService auditService) {
        this.teamService = teamService;
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.inviteMapper = inviteMapper;
        this.articleQueryService = articleQueryService;
        this.taxonomyService = taxonomyService;
        this.auditService = auditService;
    }

    @Transactional
    public void dissolve(long userId, long teamId) {
        TeamAccess access = teamService.requireOwner(userId, teamId);
        long members = memberMapper.countActive(teamId);
        articleQueryService.unshareAllFromTeam(teamId);
        taxonomyService.deleteTeamTaxonomy(teamId);
        inviteMapper.deleteByTeam(teamId);
        memberMapper.deleteByTeam(teamId);
        auditService.record(teamId, userId, "TEAM_DISSOLVE", "TEAM", teamId,
                Map.of("name", access.team().getName(), "members", members));
        teamMapper.softDelete(teamId);
    }
}
