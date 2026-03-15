package com.hzy.hzypicturebackend.api.imagesearch;

import com.hzy.hzypicturebackend.api.imagesearch.model.ImageSearchResult;
import com.hzy.hzypicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.hzy.hzypicturebackend.api.imagesearch.sub.GetImageListApi;
import com.hzy.hzypicturebackend.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://c-ssl.dtstatic.com/uploads/blog/202307/12/4ESWO2Y7UogV1gO.thumb.1000_0.jpeg";
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}
