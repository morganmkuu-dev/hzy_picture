package com.hzy.hzypicturebackend.manager.cleanup;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzy.hzypicturebackend.model.entity.Picture;
import com.hzy.hzypicturebackend.service.PictureService;
import com.hzy.hzypicturebackend.manager.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class PictureCleanupTask {

    @Resource
    private PictureService pictureService;

    @Resource
    private CosManager cosManager;

    // ----------------- 任务 1：清理 24 小时前的草稿 -----------------
    @Scheduled(cron = "0 0 2 * * ?")
    // 10秒，测试用
    //@Scheduled(cron = "0/10 * * * * ?")
    public void clearDraftPictures() {
        log.info("定时任务启动：开始清理废弃的草稿图片...");

        long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
        Date timeLimit = new Date(oneDayAgo);

        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Picture::getIs_draft, 1)
                .lt(Picture::getCreateTime, timeLimit);

        List<Picture> garbagePictures = pictureService.list(queryWrapper);

        if (garbagePictures.isEmpty()) {
            log.info("定时任务结束：没有需要清理的垃圾草稿图片。");
            return;
        }

        deletePictureFilesAndRecords(garbagePictures);
        log.info("定时任务结束：成功清理了 {} 条垃圾草稿图片！", garbagePictures.size());
    }

    // ----------------- 任务 2：清理一年前的旧数据 -----------------
    @Scheduled(cron = "0 0 3 * * ?")
    public void clearOldPictures() {
        log.info("定时任务启动：开始清理编辑和更新时间超过一年的旧数据...");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        Date oneYearAgo = calendar.getTime();

        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        // ✨ 请确保这里是你的实体类实际的编辑和更新时间字段
        queryWrapper.lt(Picture::getEditTime, oneYearAgo)
                .lt(Picture::getUpdateTime, oneYearAgo);

        List<Picture> oldPictures = pictureService.list(queryWrapper);

        if (oldPictures.isEmpty()) {
            log.info("定时任务结束：没有需要清理的一年前旧图片。");
            return;
        }

        deletePictureFilesAndRecords(oldPictures);
        log.info("定时任务结束：成功清理了 {} 条一年以上的旧图片数据！", oldPictures.size());
    }

    // ----------------- 公共方法：执行 COS 和数据库的物理删除 -----------------
    private void deletePictureFilesAndRecords(List<Picture> pictureList) {
        for (Picture picture : pictureList) {
            String originalUrl = picture.getUrl();

            // 1. 删除 COS 中的原图
            deleteCosFile(originalUrl);

            // 2. 删除 COS 中的缩略图（假设你的数据库存了缩略图路径）
            // ✨ 如果没存，也需要像下面推导 WebP 一样去推导缩略图的 Key
            deleteCosFile(picture.getThumbnailUrl());

            // 3. 推导并删除 COS 中的 WebP 图（数据库没存的情况）
            if (StrUtil.isNotBlank(originalUrl)) {
                try {
                    String originalKey = new URL(originalUrl).getPath();
                    // 核心推导逻辑：找到最后一个 "."，把后面的后缀替换为 ".webp"
                    // 例如：/test/img/123.png -> /test/img/123.webp
                    if (originalKey.contains(".")) {
                        String webpKey = StrUtil.subBefore(originalKey, ".", true) + ".webp";
                        cosManager.deleteObject(webpKey);
                        log.info("已成功推导并删除腾讯云 COS 中的 WebP 文件，Key: {}", webpKey);
                    }
                } catch (MalformedURLException e) {
                    log.error("推导 WebP COS 文件失败，原 URL 异常: {}", originalUrl, e);
                } catch (Exception e) {
                    log.error("删除 WebP COS 文件发生异常，原 URL: {}", originalUrl, e);
                }
            }

            // 4. 从数据库彻底删除这条记录
            pictureService.removeById(picture.getId());
            log.info("已清理废弃数据库记录及其所有 COS 关联文件，图片ID: {}", picture.getId());
        }
    }

    /**
     * 辅助方法：解析 URL 并删除 COS 中的文件
     */
    private void deleteCosFile(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return;
        }
        try {
            String key = new URL(fileUrl).getPath();
            cosManager.deleteObject(key);
            log.info("已成功删除腾讯云 COS 文件，Key: {}", key);
        } catch (MalformedURLException e) {
            log.error("解析文件 URL 失败，无法删除 COS 文件，URL: {}", fileUrl, e);
        } catch (Exception e) {
            log.error("调用 COS SDK 删除文件失败，URL: {}", fileUrl, e);
        }
    }
}