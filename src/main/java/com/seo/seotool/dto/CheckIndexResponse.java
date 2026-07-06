package com.seo.seotool.dto;

public class CheckIndexResponse {
    private String url;
    private String finalUrl;
    private String httpStatus;
    private Boolean alive;
    private Boolean indexed;
    private String indexStatus;
    private String liveStatus;
    private String title;
    private String note;
    private Boolean noindex;
    private Boolean blocked;
    private Boolean loginWall;
    private String checkedAt;

    public CheckIndexResponse() {
    }

    public CheckIndexResponse(
            String url,
            String finalUrl,
            String httpStatus,
            Boolean alive,
            Boolean indexed,
            String indexStatus,
            String liveStatus,
            String title,
            String note,
            Boolean noindex,
            Boolean blocked,
            Boolean loginWall,
            String checkedAt
    ) {
        this.url = url;
        this.finalUrl = finalUrl;
        this.httpStatus = httpStatus;
        this.alive = alive;
        this.indexed = indexed;
        this.indexStatus = indexStatus;
        this.liveStatus = liveStatus;
        this.title = title;
        this.note = note;
        this.noindex = noindex;
        this.blocked = blocked;
        this.loginWall = loginWall;
        this.checkedAt = checkedAt;
    }

    public String getUrl() { return url; }
    public String getFinalUrl() { return finalUrl; }
    public String getHttpStatus() { return httpStatus; }
    public Boolean getAlive() { return alive; }
    public Boolean getIndexed() { return indexed; }
    public String getIndexStatus() { return indexStatus; }
    public String getLiveStatus() { return liveStatus; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public Boolean getNoindex() { return noindex; }
    public Boolean getBlocked() { return blocked; }
    public Boolean getLoginWall() { return loginWall; }
    public String getCheckedAt() { return checkedAt; }
}