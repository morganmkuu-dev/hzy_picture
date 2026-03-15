package com.hzy.hzypicturebackend.model.dto.space;

import lombok.Data;
import java.io.Serializable;

/**
 * 管理员发放空间扩容包请求
 */
@Data
public class SpaceResourcePackAddRequest implements Serializable {

    /**
     * 要扩容的空间 id
     */
    private Long spaceId;

    /**
     * 增加的容量大小（单位：字节）
     * 前端可以传 MB，后端接收前自己转，或者前端直接传算好的字节数
     */
    private Long addSize;

    /**
     * 增加的图片数量
     */
    private Long addCount;

    /**
     * 发放原因或扩容包名称（选填）
     */
    private String packName;

    private static final long serialVersionUID = 1L;
}