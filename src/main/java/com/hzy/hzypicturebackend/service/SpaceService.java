package com.hzy.hzypicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzy.hzypicturebackend.model.dto.space.SpaceAddRequest;
import com.hzy.hzypicturebackend.model.dto.space.SpaceQueryRequest;
import com.hzy.hzypicturebackend.model.dto.space.SpaceResourcePackAddRequest;
import com.hzy.hzypicturebackend.model.entity.Picture;
import com.hzy.hzypicturebackend.model.entity.Space;
import com.hzy.hzypicturebackend.model.entity.User;
import com.hzy.hzypicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author 34332
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-02-27 16:19:56
*/
public interface SpaceService extends IService<Space> {
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    void validSpace(Space space, boolean add);

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    void fillSpaceBySpaceLevel(Space space);
    /**
     * 删除空间以及空间下的所有图片
     *
     * @param spaceId 空间id
     * @return 是否成功
     */
    boolean deleteSpaceAndPictures(long spaceId);

    void checkAndFixSpaceQuota();

    /**
     * 管理员发放空间扩容包
     *
     * @param request
     * @param loginUser
     * @return
     */
    boolean addResourcePack(SpaceResourcePackAddRequest request, User loginUser);

    void checkSpaceAuth(User loginUser, Space space);
}
