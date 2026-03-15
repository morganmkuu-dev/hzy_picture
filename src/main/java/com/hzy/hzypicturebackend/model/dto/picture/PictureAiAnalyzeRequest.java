package com.hzy.hzypicturebackend.model.dto.picture;

import lombok.Data;
import java.io.Serializable;

@Data
public class PictureAiAnalyzeRequest implements Serializable {
    private String pictureUrl;
    private static final long serialVersionUID = 1L;
}
