package com.hzy.hzypicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hzy.hzypicturebackend.model.dto.space.analyze.*;
import com.hzy.hzypicturebackend.model.entity.Space;
import com.hzy.hzypicturebackend.model.entity.User;
import com.hzy.hzypicturebackend.model.vo.space.analyze.*;

import java.util.List;

/**
* @author 34332
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-02-27 16:19:56
*/
public interface SpaceAnalyzeService extends IService<Space> {

    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
