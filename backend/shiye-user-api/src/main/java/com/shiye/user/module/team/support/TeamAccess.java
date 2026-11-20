package com.shiye.user.module.team.support;

import com.shiye.user.module.team.domain.entity.TeamEntity;

public record TeamAccess(TeamEntity team, String role) {

    public boolean isOwner() {
        return "OWNER".equals(role);
    }

    public boolean isManager() {
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }
}
