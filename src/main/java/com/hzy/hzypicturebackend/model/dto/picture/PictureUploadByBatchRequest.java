package com.hzy.hzypicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureUploadByBatchRequest implements Serializable {
  
    /**  
     * 搜索词  
     */  
    private String searchText;  
  
    /**  
     * 抓取数量  
     */  
    private Integer count = 10;
    /**
     * 名称前缀
     */
    private String namePrefix;
    // ✨ 新增：批量设置的分类
    private String category;

    // ✨ 新增：批量设置的标签列表
    private List<String> tags;

    /**
     * ✨ 新增：抓取偏移量（首图索引，防止重复抓取）
     */
    private Integer fetchOffset;

    private static final long serialVersionUID = 1L;
}
