package com.hzy.hzypicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzy.hzypicturebackend.exception.BusinessException;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import com.hzy.hzypicturebackend.exception.ThrowUtils;
//import com.hzy.hzypicturebackend.manager.sharding.DynamicShardingManager;
import com.hzy.hzypicturebackend.mapper.PictureMapper;
import com.hzy.hzypicturebackend.mapper.SpaceMapper;
import com.hzy.hzypicturebackend.model.dto.space.SpaceAddRequest;
import com.hzy.hzypicturebackend.model.dto.space.SpaceQueryRequest;
import com.hzy.hzypicturebackend.model.dto.space.SpaceResourcePackAddRequest;
import com.hzy.hzypicturebackend.model.entity.*;
import com.hzy.hzypicturebackend.model.enums.SpaceLevelEnum;
import com.hzy.hzypicturebackend.model.enums.SpaceRoleEnum;
import com.hzy.hzypicturebackend.model.enums.SpaceTypeEnum;
import com.hzy.hzypicturebackend.model.vo.SpaceVO;
import com.hzy.hzypicturebackend.model.vo.UserVO;
import com.hzy.hzypicturebackend.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author 34332
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2026-02-27 16:19:56
*/
@Slf4j
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {

    @Resource
    private UserService userService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    @Lazy
    private PictureService pictureService;
    @Resource
    private SpaceResourcePackService spaceResourcePackService;
    @Resource
    private PictureMapper pictureMapper;
    @Resource
    private SpaceUserService spaceUserService;
//    @Resource
//    @Lazy
//    private DynamicShardingManager dynamicShardingManager;
    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (spaceAddRequest.getSpaceType() == null) {
            spaceAddRequest.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 填充数据
        this.fillSpaceBySpaceLevel(space);
        // 数据校验
        this.validSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 权限校验
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 针对用户进行加锁
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                        .exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间仅能创建一个");
                // 写入数据库
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                // 如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 创建分表（仅对旗舰版的团队空间生效）
                //dynamicShardingManager.createSpacePictureTable(space);

                // 返回新写入的数据 id
                return space.getId();
            });
            // 返回结果是包装类，可以做一些处理
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    /**
     * 校验参数
     * @param space
     * @param add
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，任何异常都回滚
    public boolean deleteSpaceAndPictures(long spaceId) {
        // 1. 先删除该空间下的所有图片
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        pictureQueryWrapper.eq("spaceId", spaceId);

        // 执行批量删除（通常在 MyBatis-Plus 中配置了逻辑删除的话，这里会自动变成 UPDATE isDelete = 1）
        pictureService.remove(pictureQueryWrapper);

        // 2. 再删除空间本身
        return this.removeById(spaceId);
    }
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {

        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 填充空间
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 刷新历史空间大小
     */
    @Override
    public void checkAndFixSpaceQuota() {
        log.info("开始执行【校准历史空间大小】任务...");

        // 1. 查出所有存在的空间
        List<Space> spaceList = this.list();
        if (CollUtil.isEmpty(spaceList)) {
            log.info("没有空间需要校准。");
            return;
        }

        int fixCount = 0; // 记录被修复的空间数量

        // 遍历每个空间，利用 SQL 聚合函数精准计算真实容量
        for (Space space : spaceList) {
            Long spaceId = space.getId();

            // 构造聚合查询：统计该空间下所有【正式图片（is_draft = 0）】的总数和总大小
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("COUNT(*) AS totalCount", "IFNULL(SUM(picSize), 0) AS totalSize")
                    .eq("spaceId", spaceId)
                    .eq("is_draft", 0);

            // 查询聚合结果（COUNT 和 SUM 必定返回一条记录）
            List<Map<String, Object>> maps = pictureMapper.selectMaps(queryWrapper);
            if (CollUtil.isNotEmpty(maps)) {
                Map<String, Object> map = maps.get(0);

                // 注意：不同数据库/配置下 Map 的 Key 可能大小写不同，这里做个兼容容错
                long realTotalCount = 0L;
                long realTotalSize = 0L;

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = entry.getKey().toLowerCase();
                    if ("totalcount".equals(key) && entry.getValue() != null) {
                        realTotalCount = ((Number) entry.getValue()).longValue();
                    } else if ("totalsize".equals(key) && entry.getValue() != null) {
                        realTotalSize = ((Number) entry.getValue()).longValue();
                    }
                }

                // 3. 对比现有的容量，如果不一致，则进行修复更新
                if (space.getTotalCount() != realTotalCount || space.getTotalSize() != realTotalSize) {
                    log.info("发现空间数据异常！空间ID: {}, 原数量: {}, 原大小: {} -> 真实数量: {}, 真实大小: {}",
                            spaceId, space.getTotalCount(), space.getTotalSize(), realTotalCount, realTotalSize);

                    Space updateSpace = new Space();
                    updateSpace.setId(spaceId);
                    updateSpace.setTotalCount(realTotalCount);
                    updateSpace.setTotalSize(realTotalSize);

                    this.updateById(updateSpace);
                    fixCount++;
                }
            }
        }

        log.info("【校准历史空间大小】任务执行完毕！共修复了 {} 个空间的数据。", fixCount);
    }

    /**
     * 空间扩容
     * @param request
     * @param loginUser
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addResourcePack(SpaceResourcePackAddRequest request, User loginUser) {
        Long spaceId = request.getSpaceId();
        Long addSize = request.getAddSize();
        Long addCount = request.getAddCount();

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(addSize == null && addCount == null, ErrorCode.PARAMS_ERROR, "扩容大小和数量不能同时为空");

        // 2. 获取目标空间
        Space space = this.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

        // 3. 更新空间的容量和数量上限
        long newMaxSize = space.getMaxSize() + (addSize != null ? addSize : 0L);
        long newMaxCount = space.getMaxCount() + (addCount != null ? addCount : 0L);

        Space updateSpace = new Space();
        updateSpace.setId(spaceId);
        updateSpace.setMaxSize(newMaxSize);
        updateSpace.setMaxCount(newMaxCount);
        boolean updateResult = this.updateById(updateSpace);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "空间扩容失败");

         //4. (可选但强烈建议) 记录日志到 space_resource_pack 表
         SpaceResourcePack packLog = new SpaceResourcePack();
         packLog.setSpaceId(spaceId);
         packLog.setUserId(space.getUserId());
         packLog.setAdminId(loginUser.getId());
         packLog.setAddSize(addSize != null ? addSize : 0L);
         packLog.setAddCount(addCount != null ? addCount : 0L);
         packLog.setPackName(request.getPackName());
         spaceResourcePackService.save(packLog);
        return true;
    }
    @Override
    public void checkSpaceAuth(User loginUser, Space space){
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

}




