package com.shiye.user.module.tag.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.tag.domain.dto.TagUpdateRequest;
import com.shiye.user.module.tag.domain.vo.TagListVO;
import com.shiye.user.module.tag.domain.vo.TagVO;
import com.shiye.user.module.tag.service.TagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PatchMapping("/{id}")
    public Result<TagVO> update(@PathVariable long id,
                                @Valid @RequestBody TagUpdateRequest request) {
        return Result.ok(tagService.update(UserContext.requireUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        tagService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
