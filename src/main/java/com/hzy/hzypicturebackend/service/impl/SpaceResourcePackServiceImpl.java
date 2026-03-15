package com.hzy.hzypicturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzy.hzypicturebackend.model.entity.SpaceResourcePack;
import com.hzy.hzypicturebackend.service.SpaceResourcePackService;
import com.hzy.hzypicturebackend.mapper.SpaceResourcePackMapper;
import org.springframework.stereotype.Service;

/**
 * @description 针对表【space_resource_pack(空间资源扩容记录表)】的数据库操作Service实现
 */
@Service
public class SpaceResourcePackServiceImpl extends ServiceImpl<SpaceResourcePackMapper, SpaceResourcePack>
        implements SpaceResourcePackService {

}