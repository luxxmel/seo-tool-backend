package com.seo.seotool.dto;

import lombok.Data;

@Data
public class AuditRequest {
    private String domain;
    private Integer maxUrls;
    private Boolean checkIndex;
}