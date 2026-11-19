package com.shiye.user.module.tag.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.tag.domain.vo.TagListVO;
import com.shiye.user.module.tag.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public Result<TagListVO> list() {
        return Result.ok(new TagListVO(tagService.list(UserContext.requireUserId())));
    }
}
