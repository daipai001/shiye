package com.shiye.user.module.team.service;

import com.shiye.user.module.article.service.ArticleQueryService;
import com.shiye.user.module.team.mapper.TeamInviteMapper;
import com.shiye.user.module.team.mapper.TeamMapper;
import com.shiye.user.module.team.mapper.TeamMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public TeamDissolveService(TeamService teamService,
                               TeamMapper teamMapper,
                               TeamMemberMapper memberMapper,
                               TeamInviteMapper inviteMapper,
                               ArticleQueryService articleQueryService) {
        this.teamService = teamService;
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.inviteMapper = inviteMapper;
        this.articleQueryService = articleQueryService;
    }

    @Transactional
    public void dissolve(long userId, long teamId) {
        teamService.requireOwner(userId, teamId);
        articleQueryService.unshareAllFromTeam(teamId);
        inviteMapper.deleteByTeam(teamId);
        memberMapper.deleteByTeam(teamId);
        teamMapper.softDelete(teamId);
    }
}
