package com.hzy.hzypicturebackend.controller;

import com.hzy.hzypicturebackend.common.BaseResponse;
import com.hzy.hzypicturebackend.common.ResultUtils;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import com.hzy.hzypicturebackend.exception.ThrowUtils;
import com.hzy.hzypicturebackend.model.dto.space.analyze.SpaceCategoryAnalyzeRequest;
import com.hzy.hzypicturebackend.model.entity.User;
import com.hzy.hzypicturebackend.model.vo.space.analyze.SpaceCategoryAnalyzeResponse;
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
public class SpaceCategoryAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 获取分类信息
     * @param spaceCategoryAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(@RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceCategoryAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

}
