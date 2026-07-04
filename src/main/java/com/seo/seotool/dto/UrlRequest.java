package com.seo.seotool.dto;

import lombok.Data;
import java.util.List;

@Data // Tự động tạo getter, setter nhờ Lombok
public class UrlRequest {
    private List<String> urls;
}