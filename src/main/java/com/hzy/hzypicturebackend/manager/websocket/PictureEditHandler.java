package com.hzy.hzypicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.hzy.hzypicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.hzy.hzypicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.hzy.hzypicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.hzy.hzypicturebackend.manager.websocket.model.dto.PictureEditState;
import com.hzy.hzypicturebackend.manager.websocket.model.enums.PictureEditActionEnum;
import com.hzy.hzypicturebackend.manager.websocket.model.enums.PictureEditMessageTypeEnum;
import com.hzy.hzypicturebackend.model.entity.User;
import com.hzy.hzypicturebackend.service.UserService;
import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 图片编辑WebSocket处理器
 */
@Slf4j
@Component
public class PictureEditHandler extends TextWebSocketHandler {
    @Resource
    private UserService userService;
    @Resource
    private PictureEditEventProducer pictureEditEventProducer;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws Exception {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            ObjectMapper objectMapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);

        // 带防护机制的初始状态推送
        try {
            Long editingUserId = pictureEditingUsers.get(pictureId);
            if (editingUserId != null) {
                User editingUser = userService.getById(editingUserId);
                if (editingUser != null) {
                    PictureEditResponseMessage syncMessage = new PictureEditResponseMessage();
                    syncMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
                    syncMessage.setMessage(String.format("%s正在编辑图片", editingUser.getUserName()));
                    syncMessage.setUser(userService.getUserVO(editingUser));

                    ObjectMapper objectMapper = new ObjectMapper();
                    SimpleModule module = new SimpleModule();
                    module.addSerializer(Long.class, ToStringSerializer.instance);
                    module.addSerializer(Long.TYPE, ToStringSerializer.instance);
                    objectMapper.registerModule(module);

                    String syncMsgStr = objectMapper.writeValueAsString(syncMessage);
                    Thread.sleep(100);

                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(syncMsgStr));
                        log.info("成功给新连入的用户 {} 推送了当前编辑状态", user.getUserName());
                    }

                    // ✨ 新增：同步完编辑者状态后，继续检查并同步 Redis 中的图片操作快照
                    String redisKey = "picture:edit:state:" + pictureId;
                    String stateStr = stringRedisTemplate.opsForValue().get(redisKey);
                    if (StrUtil.isNotBlank(stateStr)) {
                        PictureEditState editState = JSONUtil.toBean(stateStr, PictureEditState.class);
                        PictureEditResponseMessage actionSyncMsg = new PictureEditResponseMessage();
                        actionSyncMsg.setType(PictureEditMessageTypeEnum.SYNC_ACTION.getValue());
                        actionSyncMsg.setMessage("正在同步图片最新编辑状态...");
                        actionSyncMsg.setEditState(editState);
                        actionSyncMsg.setUser(userService.getUserVO(editingUser)); // 发送人标记为当前编辑者

                        String actionSyncMsgStr = objectMapper.writeValueAsString(actionSyncMsg);
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(actionSyncMsgStr));
                            log.info("成功给新连入的用户 {} 同步了图片形变状态", user.getUserName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("推送当前编辑状态失败, pictureId: {}", pictureId, e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");

        // 生产消息
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        if (!pictureEditingUsers.containsKey(pictureId)) {
            pictureEditingUsers.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        } else {
            // 死锁拒绝响应
            PictureEditResponseMessage errorMsg = new PictureEditResponseMessage();
            errorMsg.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            errorMsg.setMessage("当前已有用户正在编辑，请稍后再试");
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(errorMsg)));
            }
        }
    }

    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        if (editingUserId != null && editingUserId.equals(user.getId())) {

            // ✨ 新增：将用户的操作累计到 Redis 中
            String redisKey = "picture:edit:state:" + pictureId;
            PictureEditState editState = new PictureEditState();
            String stateStr = stringRedisTemplate.opsForValue().get(redisKey);
            if (StrUtil.isNotBlank(stateStr)) {
                editState = JSONUtil.toBean(stateStr, PictureEditState.class);
            }

            // 累计操作
            switch (actionEnum) {
                case ROTATE_LEFT: editState.setRotate(editState.getRotate() - 1); break;
                case ROTATE_RIGHT: editState.setRotate(editState.getRotate() + 1); break;
                case ZOOM_IN: editState.setZoom(editState.getZoom() + 1); break;
                case ZOOM_OUT: editState.setZoom(editState.getZoom() - 1); break;
            }
            // 设置 1 小时过期兜底，防止内存泄漏
            stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(editState), 1, TimeUnit.HOURS);

            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            pictureEditingUsers.remove(pictureId);

            // ✨ 新增：退出编辑时，说明一轮编辑结束，清空 Redis 里的操作草稿
            stringRedisTemplate.delete("picture:edit:state:" + pictureId);

            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");

        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            handleExitEditMessage(null, session, user, pictureId);
        }

        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }
}