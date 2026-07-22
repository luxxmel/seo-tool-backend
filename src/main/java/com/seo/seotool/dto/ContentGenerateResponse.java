package com.seo.seotool.dto;

public class ContentGenerateResponse {

    private boolean success;

    private String message;

    private String content;

    private String title;

    private String metaDescription;

    private String outline;

    private long generatedAt;

    public ContentGenerateResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMetaDescription() {
        return metaDescription;
    }

    public void setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(long generatedAt) {
        this.generatedAt = generatedAt;
    }

    public static ContentGenerateResponse success(
            String title,
            String metaDescription,
            String outline,
            String content
    ) {

        ContentGenerateResponse response = new ContentGenerateResponse();

        response.setSuccess(true);
        response.setMessage("Generate content thành công.");
        response.setTitle(title);
        response.setMetaDescription(metaDescription);
        response.setOutline(outline);
        response.setContent(content);
        response.setGeneratedAt(System.currentTimeMillis());

        return response;
    }

    public static ContentGenerateResponse error(String message) {

        ContentGenerateResponse response = new ContentGenerateResponse();

        response.setSuccess(false);
        response.setMessage(message);
        response.setGeneratedAt(System.currentTimeMillis());

        return response;
    }
}