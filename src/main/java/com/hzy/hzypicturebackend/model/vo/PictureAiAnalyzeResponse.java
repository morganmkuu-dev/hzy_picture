package com.hzy.hzypicturebackend.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class PictureAiAnalyzeResponse {
    private String introduction;
    private String category;
    private List<String> tags;
}
