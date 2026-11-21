package com.shiye.user.module.team.domain.vo;

import java.util.List;

public record TeamTaxonomyOptionsVO(
        List<TeamTagOptionVO> tags,
        List<TeamFolderOptionVO> folders
) {
}
