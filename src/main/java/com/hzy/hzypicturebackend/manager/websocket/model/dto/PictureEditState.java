package com.hzy.hzypicturebackend.manager.websocket.model.dto;

import lombok.Data;

/**
 * 图片编辑累计状态
 */
@Data
public class PictureEditState {
    // 旋转次数（正数为向右，负数为向左）
    private int rotate = 0;
    // 缩放次数（正数为放大，负数为缩小）
    private int zoom = 0;
}