package com.hzy.hzypicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防止重复提交注解
 *
 * @author hzy
 */
@Target(ElementType.METHOD) // 作用于方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时生效
public @interface NoRepeatSubmit {

    /**
     * 锁定时间（默认 3000 毫秒）
     * 在这段时间内，同一个用户针对同一个接口发起的相同参数请求会被拒绝
     */
    int interval() default 3000;
}