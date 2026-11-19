package com.shiye.admin.module.overview.controller;

import com.shiye.admin.module.overview.domain.vo.OverviewVO;
import com.shiye.admin.module.overview.service.OverviewService;
import com.shiye.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public Result<OverviewVO> overview() {
        return Result.ok(overviewService.overview());
    }
}
