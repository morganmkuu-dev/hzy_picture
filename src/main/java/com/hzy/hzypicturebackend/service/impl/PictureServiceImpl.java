package com.hzy.hzypicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzy.hzypicturebackend.api.aliyunai.AliYunAIApi;
import com.hzy.hzypicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.hzy.hzypicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.hzy.hzypicturebackend.constant.PictureConstant;
import com.hzy.hzypicturebackend.exception.BusinessException;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import com.hzy.hzypicturebackend.exception.ThrowUtils;
import com.hzy.hzypicturebackend.manager.CosManager;
import com.hzy.hzypicturebackend.manager.auth.SpaceUserAuthManager;
import com.hzy.hzypicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.hzy.hzypicturebackend.manager.upload.FilePictureUpload;
import com.hzy.hzypicturebackend.manager.upload.PictureUploadTemplate;
import com.hzy.hzypicturebackend.manager.upload.UrlPictureUpload;
import com.hzy.hzypicturebackend.model.dto.file.UploadPictureResult;
import com.hzy.hzypicturebackend.model.dto.picture.*;
import com.hzy.hzypicturebackend.model.entity.Picture;
import com.hzy.hzypicturebackend.model.entity.Space;
import com.hzy.hzypicturebackend.model.entity.User;
import com.hzy.hzypicturebackend.model.enums.PictureReviewStatusEnum;
import com.hzy.hzypicturebackend.model.vo.PictureVO;
import com.hzy.hzypicturebackend.model.vo.UserVO;
import com.hzy.hzypicturebackend.service.PictureService;
import com.hzy.hzypicturebackend.mapper.PictureMapper;
import com.hzy.hzypicturebackend.service.SpaceService;
import com.hzy.hzypicturebackend.service.UserService;
import com.hzy.hzypicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 34332
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-02-21 16:12:53
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService{

    @Resource
    private UserService userService;
    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;
    @Resource
    private CosManager cosManager;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private AliYunAIApi aliYunAIApi;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 空间额度校验 (前置防御)
        Long spaceId = pictureUploadRequest != null ? pictureUploadRequest.getSpaceId() : null;
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验额度 (这一步保留，作为防御性拦截，防止空间满了还疯狂传草稿)
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }

        Picture oldPicture = null;
        // 如果是更新图片，需要校验图片是否存在并进行【动态权限校验】
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 1. 校验空间是否一致，并补齐 spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }

            // ==========================================
            // ✨ 核心修复：升级为动态权限校验逻辑
            // ==========================================
            if (spaceId != null) {
                // 属于空间的图片，必须使用 SpaceUserAuthManager 鉴权！
                Space space = spaceService.getById(spaceId);
                ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
                List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
                if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "您没有该空间的图片编辑权限");
                }
            } else {
                // 公共图库的图片，依旧走老逻辑：仅本人或管理员可编辑
                if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "您没有权限修改该图片");
                }
            }
        }

        if (inputSource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片为空");
        }

        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);

        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicColor(uploadPictureResult.getPicColor());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());

        // 刚上传一律为草稿
        picture.setIs_draft(1);
        picture.setUserId(loginUser.getId());
        fillReviewParams(picture, loginUser);

        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 开启事务
        Long finalSpaceId = spaceId;
        Picture finalOldPicture = oldPicture;
        Long finalPictureId = pictureId;

        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

            if (finalSpaceId != null) {
                // 核心修复 1：当前上传的一律是草稿（is_draft = 1），【绝对不】增加新的空间额度！
                // 但是，如果这是在【编辑/更新图片】，且老图片是【正式图片】（is_draft = 0），
                // 说明用户重新上传了文件，导致正式图片变回了草稿！
                // 此时我们需要把老图片之前占用的额度给【退还】回去，防止虚假占用！
                if (finalPictureId != null && finalOldPicture != null) {
                    if (finalOldPicture.getIs_draft() == 0) {
                        boolean update = spaceService.lambdaUpdate()
                                .eq(Space::getId, finalSpaceId)
                                .setSql("totalSize = totalSize - " + finalOldPicture.getPicSize())
                                .setSql("totalCount = totalCount - 1")
                                .update();
                        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                    }
                }
            }
            return picture;
        });
        return PictureVO.objToVo(picture);
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");

        String category = pictureUploadByBatchRequest.getCategory();
        List<String> tags = pictureUploadByBatchRequest.getTags();
        Integer fetchOffset = pictureUploadByBatchRequest.getFetchOffset();
        if (fetchOffset == null || fetchOffset < 1) {
            fetchOffset = 1;
        }

        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1&first=%s", searchText, fetchOffset);

        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }

        Elements elementList = div.select("a.iusc");
        int uploadCount = 0;

        for (Element element : elementList) {
            String mData = element.attr("m");
            if (StrUtil.isBlank(mData)) {
                continue;
            }

            String fileUrl;
            try {
                fileUrl = cn.hutool.json.JSONUtil.parseObj(mData).getStr("murl");
            } catch (Exception e) {
                log.error("解析图片高清 URL 失败", e);
                continue;
            }

            if (StrUtil.isBlank(fileUrl)) {
                continue;
            }

            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
            if (StrUtil.isBlank(namePrefix)) {
                namePrefix = searchText;
            }

            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if (StrUtil.isNotBlank(namePrefix)) {
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }
            pictureUploadRequest.setFileUrl(fileUrl);

            try {
                // 上传完是草稿 (不占额度)
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());

                Picture updatePicture = new Picture();
                updatePicture.setId(pictureVO.getId());
                // ✨ 批量上传瞬间转正
                updatePicture.setIs_draft(0);

                if (StrUtil.isNotBlank(category)) {
                    updatePicture.setCategory(category);
                }
                if (cn.hutool.core.collection.CollUtil.isNotEmpty(tags)) {
                    updatePicture.setTags(cn.hutool.json.JSONUtil.toJsonStr(tags));
                }

                // ✨ 核心修复 2：批量上传转正时，如果有空间信息，必须加上额度
                Picture savedPicture = this.getById(pictureVO.getId());
                transactionTemplate.execute(status -> {
                    this.updateById(updatePicture);
                    if (savedPicture.getSpaceId() != null) {
                        boolean update = spaceService.lambdaUpdate()
                                .eq(Space::getId, savedPicture.getSpaceId())
                                .setSql("totalSize = totalSize + " + savedPicture.getPicSize())
                                .setSql("totalCount = totalCount + 1")
                                .update();
                        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                    }
                    return true;
                });

                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        //已经改为使用注解鉴权
        //checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

            // ✨ 核心修复 3：释放额度（仅当删除的图片是【正式图片】时，才释放额度！）
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null && oldPicture.getIs_draft() == 0) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        picture.setEditTime(new Date());
        this.validPicture(picture);

        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //已经改为使用注解鉴权
        //checkPictureAuth(loginUser, oldPicture);
        this.fillReviewParams(picture, loginUser);

        // 用户提交编辑，证明填写完成，草稿变为正式图片
        picture.setIs_draft(0);

        // ✨ 核心修复 4：草稿转正时，正式执行配额扣减
        transactionTemplate.execute(status -> {
            boolean result = this.updateById(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

            Long spaceId = oldPicture.getSpaceId();
            // 如果是从草稿变成正式图片，才需要真正加上额度
            if (spaceId != null && oldPicture.getIs_draft() == 1) {
                Space space = spaceService.getById(spaceId);
                ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
                if (space.getTotalCount() >= space.getMaxCount()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
                }
                if (space.getTotalSize() + oldPicture.getPicSize() > space.getMaxSize()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
                }

                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize + " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
    }

    /**
     * 颜色搜索
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR, "主色调不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
            }
        }

        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(Picture::getPicColor);

        if (spaceId != null) {
            queryWrapper.eq(Picture::getSpaceId, spaceId);
        } else {
            queryWrapper.isNull(Picture::getSpaceId);
            if (!userService.isAdmin(loginUser)) {
                queryWrapper.eq(Picture::getReviewStatus, 1);
            }
        }

        List<Picture> pictureList = this.list(queryWrapper);

        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }

        Color targetColor = Color.decode(picColor);

        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12)
                .collect(Collectors.toList());

        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Long spaceId = pictureQueryRequest.getSpaceId();

        Boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();

        // 默认只查出非草稿的正式图片
        queryWrapper.eq("is_draft", 0);

        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId != null && nullSpaceId, "spaceId");

        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);

        // ==========================================
        // 精准拦截“其他”类型
        // ==========================================
        if ("其他".equals(category)) {
            // SQL 逻辑：查询 category 不在标准列表里的，或者 category 为空的
            queryWrapper.and(qw -> qw.notIn("category", PictureConstant.DEFAULT_CATEGORY_LIST).or().isNull("category"));
        } else {
            // 正常的分类查询
            queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        }

        queryWrapper.le(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.le(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);

        // ==========================================
        // 精准拦截“其他”标签
        // ==========================================
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                if ("其他".equals(tag)) {
                    // SQL 逻辑：查询 tags 为空的，或者 tags 里绝对不包含标准标签的记录
                    queryWrapper.and(qw -> {
                        qw.isNull("tags").or(orQw -> {
                            for (String standardTag : PictureConstant.DEFAULT_TAG_LIST) {
                                // 循环拼接 notLike，MyBatis-Plus 默认用 AND 连接
                                orQw.notLike("tags", "\"" + standardTag + "\"");
                            }
                        });
                    });
                } else {
                    // 正常的标签查询
                    queryWrapper.like("tags", "\"" + tag + "\"");
                }
            }
        }

        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1. 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR, "请选择要操作的图片");

        // 2. 校验权限 (分叉逻辑：私有空间 vs 公共图库)
        if (spaceId != null) {
            // 私有空间：校验空间是否存在，以及是否有权限
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
            }
        } else {
            // 公共图库：通常仅管理员可操作 (根据你的实际角色常量调整，比如 UserConstant.ADMIN_ROLE)
            if (!"admin".equals(loginUser.getUserRole())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可批量编辑公共图库图片");
            }
        }

        // 3. 查询指定图片，仅选择需要的字段
        // ✨ 核心修复：巧妙利用 MyBatis-Plus 的条件构造功能，兼容 null 的情况
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(spaceId != null, Picture::getSpaceId, spaceId) // 如果 spaceId 不为空，拼接 = spaceId
                .isNull(spaceId == null, Picture::getSpaceId)      // 如果 spaceId 为空，拼接 IS NULL
                .in(Picture::getId, pictureIdList)
                .list();

        if (pictureList.isEmpty()) {
            return;
        }

        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });

        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 校验图片
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 审核图片
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核参数1
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }


    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        if (count > 1) {
            return;
        }
        cosManager.deleteObject(oldPicture.getUrl());
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    /**
     * 图片权限校验
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 刷新主色调
     */
    @Override
    public void batchUpdatePictureColor() {
        int batchSize = 200;
        int current = 1;
        boolean hasMore = true;

        while (hasMore) {
            LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.isNull(Picture::getPicColor)
                    .or()
                    .eq(Picture::getPicColor, "");

            Page<Picture> page = new Page<>(current, batchSize);
            Page<Picture> picturePage = this.page(page, queryWrapper);
            List<Picture> records = picturePage.getRecords();

            if (records.isEmpty()) {
                hasMore = false;
                break;
            }

            for (Picture picture : records) {
                String url = picture.getUrl();
                if (url == null) {
                    continue;
                }

                try {
                    String colorUrl = url + "?imageAve";
                    HttpResponse response = HttpRequest.get(colorUrl)
                            .timeout(5000)
                            .execute();

                    if (response.isOk()) {
                        String body = response.body();
                        JSONObject jsonObject = JSONUtil.parseObj(body);
                        String rgb = jsonObject.getStr("RGB");

                        if (rgb != null) {
                            picture.setPicColor(rgb);
                            this.updateById(picture);
                            log.info("成功更新图片主色调，图片ID: {}, 颜色: {}", picture.getId(), rgb);
                        }
                    } else {
                        log.warn("获取图片主色调失败，图片ID: {}, 状态码: {}", picture.getId(), response.getStatus());
                    }
                } catch (Exception e) {
                    log.error("处理图片主色调异常，图片ID: {}", picture.getId(), e);
                }
            }
            current++;
        }
        log.info("历史图片主色调刷新任务执行完毕！");
    }

    /**
     * nameRule 格式：图片{序号}
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    /**
     * 创建扩图任务
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 权限校验
        //已经改为使用注解鉴权
        //checkPictureAuth(loginUser, picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAIApi.createOutPaintingTask(taskRequest);
    }

}