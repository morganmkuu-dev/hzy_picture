package com.hzy.hzypicturebackend.config;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信公众号相关配置
 */
@Configuration
public class WechatConfig {

    @Value("${wechat.mp.app-id:}")
    private String appId;

    @Value("${wechat.mp.secret:}")
    private String secret;

    @Bean
    public WxMpService wxMpService() {
        WxMpService wxMpService = new WxMpServiceImpl();
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId(appId);
        config.setSecret(secret);
        wxMpService.setWxMpConfigStorage(config);
        return wxMpService;
    }
}