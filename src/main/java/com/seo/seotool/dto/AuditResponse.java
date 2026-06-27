package com.seo.seotool.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuditResponse {
    private String domain;
    private String finalUrl;
    private String status;
    private String message;

    private AuditOverview overview = new AuditOverview();
    private AuditTechnical technical = new AuditTechnical();
    private AuditSearch search = new AuditSearch();

    private List<AuditIssueSummary> issueSummary = new ArrayList<>();
    private List<AuditPageRow> pageRows = new ArrayList<>();

    @Data
    public static class AuditOverview {
        private Object seoScore = "N/A";
        private Object pagesScanned = "N/A";
        private Object totalIssues = "N/A";
        private Object errors = "N/A";
        private Object warnings = "N/A";
    }

    @Data
    public static class AuditTechnical {
        private Object health = "N/A";
        private Object healthLabel = "N/A";
        private Object speed = "N/A";
        private Object avgResponseTime = "N/A";

        private Object crawlablePages = "N/A";
        private Object blockedPages = "N/A";
        private Object noindexPages = "N/A";
        private Object brokenLinks = "N/A";
        private Object internalBrokenLinks = "N/A";
        private Object redirects = "N/A";

        private Object https = "N/A";
        private Object robotsTxt = "N/A";
        private Object sitemapXml = "N/A";
    }

    @Data
    public static class AuditSearch {
        private Object indexedPages = "N/A";
        private Object topPages = "N/A";
        private Object notIndexedPages = "N/A";
        private Object unknownPages = "N/A";
        private Object titleIssues = "N/A";
        private Object descriptionIssues = "N/A";
    }

    @Data
    public static class AuditIssueSummary {
        private String name;
        private int errors;
        private int pages;
        private String icon;
        private String iconClass;

        public AuditIssueSummary() {
        }

        public AuditIssueSummary(String name, int errors, int pages, String icon, String iconClass) {
            this.name = name;
            this.errors = errors;
            this.pages = pages;
            this.icon = icon;
            this.iconClass = iconClass;
        }
    }

    @Data
    public static class AuditPageRow {
        private String url;
        private String finalUrl;
        private String status;
        private String statusClass;
        private String index;
        private String title;
        private String description;
        private String h1;
        private int issues;

        private String canonical;
        private String robots;
        private int internalLinks;
        private int externalLinks;
        private int images;
        private int missingAlt;
        private long responseTimeMs;
        private String note;
    }
}