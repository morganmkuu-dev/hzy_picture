package com.hzy.hzypicturebackend.controller;

import com.hzy.hzypicturebackend.common.BaseResponse;
import com.hzy.hzypicturebackend.common.ResultUtils;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import com.hzy.hzypicturebackend.exception.ThrowUtils;
import com.hzy.hzypicturebackend.model.dto.space.analyze.SpaceUserAnalyzeRequest;
import com.hzy.hzypicturebackend.model.entity.User;
import com.hzy.hzypicturebackend.model.vo.space.analyze.SpaceUserAnalyzeResponse;
import com.hzy.hzypicturebackend.service.SpaceAnalyzeService;
import com.hzy.hzypicturebackend.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space/analyze")
public class SpaceUserAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 用户上传行为分析
     * @param spaceUserAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(@RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceUserAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

}
