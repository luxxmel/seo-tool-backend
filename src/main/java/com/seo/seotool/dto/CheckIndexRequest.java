package com.seo.seotool.dto;

import java.util.List;

public class CheckIndexRequest {
    private String apiKey;
    private List<String> urls;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }
}