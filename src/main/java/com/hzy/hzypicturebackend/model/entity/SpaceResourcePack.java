package com.hzy.hzypicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 空间资源扩容记录
 */
@TableName(value = "space_resource_pack")
@Data
public class SpaceResourcePack implements Serializable {

    /**
     * 主键 id
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 被扩容的空间 id
     */
    private Long spaceId;

    /**
     * 获得扩容的用户 id
     */
    private Long userId;

    /**
     * 操作发放的管理员 id
     */
    private Long adminId;

    /**
     * 增加的空间大小 (字节)
     */
    private Long addSize;

    /**
     * 增加的空间条数
     */
    private Long addCount;

    /**
     * 扩容包名称/发放原因
     */
    private String packName;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}