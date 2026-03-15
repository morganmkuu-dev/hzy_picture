package com.hzy.hzypicturebackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.TimeZone;

/**
 * Spring MVC Json 配置
 */
@JsonComponent
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // 1. 使用 Jackson 提供的 builder 创建实例
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();

        // 2. 设置时区（可选，防止前后端时间差 8 小时）
        objectMapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));

        // 3. 注册自定义模块（处理 Long 类型转 String，防止前端精度丢失）
        SimpleModule module = new SimpleModule();
        // 将 Long 类型（对象）和 long 类型（基本型）都序列化为字符串
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);

        // 4. 将模块注册到真正的 Jackson mapper 中
        objectMapper.registerModule(module);

        return objectMapper;
    }
}