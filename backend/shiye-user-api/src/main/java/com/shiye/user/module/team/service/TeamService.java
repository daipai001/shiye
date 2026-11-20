package com.shiye.user.module.team.service;

import com.shiye.user.module.team.domain.dto.TeamCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamInviteCreateRequest;
import com.shiye.user.module.team.domain.dto.TeamJoinRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberAddRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberUpdateRequest;
import com.shiye.user.module.team.domain.dto.TeamUpdateRequest;
import com.shiye.user.module.team.domain.vo.TeamDetailVO;
import com.shiye.user.module.team.domain.vo.TeamInviteVO;
import com.shiye.user.module.team.domain.vo.TeamMemberVO;
import com.shiye.user.module.team.domain.vo.TeamQuota;
import com.shiye.user.module.team.domain.vo.TeamVO;
import com.shiye.user.module.team.support.TeamAccess;

import java.util.List;
import java.util.Set;

public interface TeamService {

    List<TeamVO> myTeams(long userId);

    TeamDetailVO create(long userId, TeamCreateRequest request);

    TeamDetailVO detail(long userId, long teamId);

    TeamVO update(long userId, long teamId, TeamUpdateRequest request);

    List<TeamMemberVO> members(long userId, long teamId);

    TeamMemberVO addMember(long userId, long teamId, TeamMemberAddRequest request);

    TeamInviteVO createInvite(long userId, long teamId, TeamInviteCreateRequest request);

    TeamDetailVO join(long userId, TeamJoinRequest request);

    TeamMemberVO updateMember(long userId, long teamId, long targetUserId,
                              TeamMemberUpdateRequest request);

    void removeMember(long userId, long teamId, long targetUserId);

    void leave(long userId, long teamId);

    TeamAccess requireMember(long userId, long teamId);

    TeamAccess requireManager(long userId, long teamId);

    TeamAccess requireOwner(long userId, long teamId);

    Set<Long> teamIdsForUser(long userId);

    boolean isMember(long userId, long teamId);

    TeamQuota quota(long teamId);

    long countMembers(long teamId);
}
