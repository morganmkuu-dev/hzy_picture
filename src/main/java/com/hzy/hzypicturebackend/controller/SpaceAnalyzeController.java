package com.hzy.hzypicturebackend.controller;

import com.hzy.hzypicturebackend.common.BaseResponse;
import com.hzy.hzypicturebackend.common.ResultUtils;

import com.hzy.hzypicturebackend.exception.ErrorCode;
import com.hzy.hzypicturebackend.exception.ThrowUtils;
import com.hzy.hzypicturebackend.model.dto.space.*;
import com.hzy.hzypicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.hzy.hzypicturebackend.model.entity.User;
import com.hzy.hzypicturebackend.model.vo.space.analyze.SpaceUsageAnalyzeResponse;
import com.hzy.hzypicturebackend.service.SpaceAnalyzeService;
import com.hzy.hzypicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 获取空间使用状态
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(
            @RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = spaceAnalyzeService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }
}
