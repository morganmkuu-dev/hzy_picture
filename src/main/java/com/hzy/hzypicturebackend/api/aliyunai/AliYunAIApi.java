package com.hzy.hzypicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.hzy.hzypicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.hzy.hzypicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.hzy.hzypicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.hzy.hzypicturebackend.exception.BusinessException;
import com.hzy.hzypicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AliYunAIApi {

    @Value("${dashscope.api-key}")
    private String apiKey;
    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";
    /**
     * AI分析图片
     * @param pictureUrl
     * @return
     */
    public String analyzeImage(String pictureUrl) {
        String systemPrompt = "你是一个专业的图片分析助手。请分析提供的图片，提取出图片的简介、1个主分类、以及1到3个标签。\n" +
                "【约束条件】：\n" +
                "1. 分类优先：请优先从 [\"模板\", \"电商\", \"表情包\", \"素材\", \"海报\", \"动漫\"] 中选择主分类，如果均极其不符才可自定义。\n" +
                "2. 标签优先：请优先从 [\"热门\", \"搞笑\", \"生活\", \"高清\", \"艺术\", \"校园\", \"背景\", \"简历\", \"创意\"] 中选择标签，如果不够准确可补充少许自定义标签。\n" +
                "请严格按照以下 JSON 格式返回，绝对不要输出任何额外的解释文本，也不要使用 ``` 标记：\n" +
                "{\n" +
                "  \"introduction\": \"这里是一段50字以内的图片简介\",\n" +
                "  \"category\": \"素材\",\n" +
                "  \"tags\": [\"高清\", \"创意\", \"热门\"]\n" +
                "}";

        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", pictureUrl),
                        Collections.singletonMap("text", systemPrompt)
                )).build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model("qwen-vl-max") // 使用通义千问视觉模型
                .messages(Arrays.asList(userMessage))
                .build();

        try {
            MultiModalConversationResult result = conv.call(param);
            List<Map<String, Object>> contentList = result.getOutput().getChoices().get(0).getMessage().getContent();
            for (Map<String, Object> content : contentList) {
                if (content.containsKey("text")) {
                    return content.get("text").toString();
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("AI 识别图片失败：" + e.getMessage());
        }
    }
    /**
     * 创建任务
     * @param createOutPaintingTaskRequest
     * @return
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        // 发送请求
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                // 必须开启异步处理，设置为enable。
                .header("X-DashScope-Async", "enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            String errorCode = response.getCode();
            if (StrUtil.isNotBlank(errorCode)) {
                String errorMessage = response.getMessage();
                log.error("AI 扩图失败，errorCode:{}, errorMessage:{}", errorCode, errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图接口响应异常");
            }
            return response;
        }
    }
    /**
     * 查询创建的任务
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        }
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
