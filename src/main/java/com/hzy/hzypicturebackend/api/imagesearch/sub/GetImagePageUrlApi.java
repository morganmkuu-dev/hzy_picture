package com.hzy.hzypicturebackend.api.imagesearch.sub;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.hzy.hzypicturebackend.exception.BusinessException;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取图片页面地址
     *
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        // 定义下载的文件和最终要上传的文件
        File downloadedFile = null;
        File uploadFile = null;

        try {
            // 1. 判断图片格式并下载
            boolean isWebp = imageUrl.toLowerCase().contains(".webp");
            String suffix = isWebp ? ".webp" : ".jpg";
            downloadedFile = FileUtil.file(System.getProperty("java.io.tmpdir"), "temp_search_image" + suffix);

            // 第一层伪装：下载原图
            HttpResponse downloadResponse = HttpRequest.get(imageUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .timeout(5000)
                    .execute();
            if (downloadResponse.isOk()) {
                downloadResponse.writeBody(downloadedFile);
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片下载失败，状态码：" + downloadResponse.getStatus());
            }

            // ✨ 核心修复：如果是 WebP 格式，使用 Hutool 进行格式转换
            if (isWebp) {
                uploadFile = FileUtil.file(System.getProperty("java.io.tmpdir"), "temp_search_image_converted.jpg");
                // 将 webp 转为 jpg
                ImgUtil.convert(downloadedFile, uploadFile);
                log.info("WebP 图片已成功转换为 JPG 格式");
            } else {
                uploadFile = downloadedFile;
            }

        } catch (Exception e) {
            log.error("图片下载或格式转换失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法处理测试图片，请检查图片链接是否能正常访问");
        }

        // 2. 准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", uploadFile);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_IMAGE");

        long uptime = System.currentTimeMillis();
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;

        try {
            // 3. 发送 POST 请求到百度接口
            HttpResponse response = HttpRequest.post(url)
                    // 终极绝杀：加上 acs-token
                    .header("acs-token", RandomUtil.randomString(1))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .form(formData)
                    .timeout(10000)
                    .execute();

            // 判断响应状态
            if (HttpStatus.HTTP_OK != response.getStatus()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 解析响应
            String responseBody = response.body();
            log.info("百度接口返回的原始结果：{}", responseBody);

            Map<String, Object> result = JSONUtil.toBean(responseBody, Map.class);

            // 4. 处理响应结果
            if (result == null || !Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败，百度返回：" + responseBody);
            }
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String) data.get("url");
            // 对 URL 进行解码
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            if (searchResultUrl == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效结果");
            }
            return searchResultUrl;
        } catch (Exception e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        } finally {
            // 5. 无论成功失败，将下载的原文件和转换后的文件统统清理干净
            if (downloadedFile != null && downloadedFile.exists()) {
                FileUtil.del(downloadedFile);
            }
            if (uploadFile != null && uploadFile.exists() && uploadFile != downloadedFile) {
                FileUtil.del(uploadFile);
            }
        }
    }

    public static void main(String[] args) {
        // 测试以图搜图功能（找一个 webp 的图片测试一下转换是否成功）
        String imageUrl = "https://c-ssl.dtstatic.com/uploads/blog/202307/12/4ESWO2Y7UogV1gO.thumb.1000_0.jpeg"; // 如果你有真实的 webp 链接可以换成你的
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}