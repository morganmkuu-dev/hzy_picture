package com.hzy.hzypicturebackend.controller;

import com.hzy.hzypicturebackend.common.BaseResponse;
import com.hzy.hzypicturebackend.common.ResultUtils;
import com.hzy.hzypicturebackend.exception.BusinessException;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxJsapiSignature;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/api/wechat")
public class WechatController {

    @Resource
    private WxMpService wxMpService; // WxJava 提供的核心服务类

    @GetMapping("/jsapi-signature")
    public BaseResponse<WxJsapiSignature> getJsapiSignature(@RequestParam String url) {
        try {
            // 生成微信 JS-SDK 需要的 config 参数（appId, timestamp, nonceStr, signature）
            WxJsapiSignature jsapiSignature = wxMpService.createJsapiSignature(url);
            return ResultUtils.success(jsapiSignature);
        } catch (WxErrorException e) {
            log.error("获取微信 JSAPI 签名失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取微信签名失败");
        }
    }
}