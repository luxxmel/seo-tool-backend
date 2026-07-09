package com.seo.seotool.dto;

public class ContentGenerateRequest {

    private String topic;

    private String mainKeyword;

    private String subKeywords;

    private String tone;

    private String length;

    private String note;

    public ContentGenerateRequest() {
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMainKeyword() {
        return mainKeyword;
    }

    public void setMainKeyword(String mainKeyword) {
        this.mainKeyword = mainKeyword;
    }

    public String getSubKeywords() {
        return subKeywords;
    }

    public void setSubKeywords(String subKeywords) {
        this.subKeywords = subKeywords;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getLength() {
        return length;
    }

    public void setLength(String length) {
        this.length = length;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "ContentGenerateRequest{" +
                "topic='" + topic + '\'' +
                ", mainKeyword='" + mainKeyword + '\'' +
                ", subKeywords='" + subKeywords + '\'' +
                ", tone='" + tone + '\'' +
                ", length='" + length + '\'' +
                ", note='" + note + '\'' +
                '}';
    }
}