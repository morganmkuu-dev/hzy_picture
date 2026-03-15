package com.hzy.hzypicturebackend.aop;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.extra.servlet.ServletUtil;
import com.hzy.hzypicturebackend.annotation.NoRepeatSubmit;
import com.hzy.hzypicturebackend.exception.BusinessException;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交 AOP 切面
 *
 * @author hzy
 */
@Aspect
@Component
@Slf4j
public class NoRepeatSubmitAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 环绕通知：拦截标有 @NoRepeatSubmit 注解的方法
     */
    @Around("@annotation(noRepeatSubmit)")
    public Object doAround(ProceedingJoinPoint pjp, NoRepeatSubmit noRepeatSubmit) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        
        // 1. 获取用户唯一标识 (如果已登录取 userId，未登录取 IP)
        String userId = "";
        try {
            if (StpUtil.isLogin()) {
                userId = String.valueOf(StpUtil.getLoginId());
            } else {
                userId = ServletUtil.getClientIP(request);
            }
        } catch (Exception e) {
            userId = ServletUtil.getClientIP(request);
        }

        // 2. 获取请求路径
        String uri = request.getRequestURI();

        // 3. 获取请求参数
        String argsString = Arrays.toString(pjp.getArgs());

        // 4. 将 用户标识 + 路径 + 参数 拼接并进行 MD5 哈希，生成独一无二的 Redis Key
        // 这样即使是同一个用户，访问不同接口，或者同一个接口传了不同参数，都不会被误伤
        String hash = DigestUtils.md5DigestAsHex((userId + ":" + uri + ":" + argsString).getBytes());
        String redisKey = "yupicture:no_repeat_submit:" + hash;

        // 5. 核心：利用 Redis 的 SETNX (Set if Not eXists) 机制加锁
        long interval = noRepeatSubmit.interval();
        // setIfAbsent：如果 redis 中没有这个 key，就设值并返回 true；如果已经有了，就返回 false
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1", interval, TimeUnit.MILLISECONDS);

        // 如果设置失败，说明指定时间内已经有过相同请求
        if (Boolean.FALSE.equals(isSuccess)) {
            log.warn("防重提交拦截：用户 [{}] 频繁请求接口 [{}]", userId, uri);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求过于频繁，请稍后再试");
        }

        // 6. 验证通过，放行执行实际的 Controller 业务逻辑
        return pjp.proceed();
    }
}