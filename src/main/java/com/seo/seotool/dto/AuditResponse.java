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

    private BacklinkProfile backlink = new BacklinkProfile();
    private OrganicSearch organic = new OrganicSearch();
    private PaidSearch paid = new PaidSearch();
    private SearchConsole searchConsole = new SearchConsole();

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
    public static class BacklinkProfile {
        private Object authorityScore = "N/A";
        private Object domainRating = "N/A";
        private Object urlRating = "N/A";

        private Object backlinks = "N/A";
        private Object referringDomains = "N/A";
        private Object dofollowBacklinks = "N/A";
        private Object nofollowBacklinks = "N/A";

        private Object newBacklinks = "N/A";
        private Object lostBacklinks = "N/A";

        private String dataSource = "N/A";
        private String note = "Cần kết nối API Ahrefs, Semrush, Moz, Majestic hoặc DataForSEO để lấy dữ liệu backlink thật.";

        private List<MetricItem> topAnchors = new ArrayList<>();
        private List<MetricItem> topRefDomains = new ArrayList<>();
        private List<MetricItem> topLinkedPages = new ArrayList<>();
    }

    @Data
    public static class OrganicSearch {
        private Object organicKeywords = "N/A";
        private Object organicTraffic = "N/A";
        private Object trafficValue = "N/A";
        private Object topCountries = "N/A";

        private String dataSource = "N/A";
        private String note = "Cần kết nối Google Search Console, Ahrefs, Semrush hoặc DataForSEO để lấy keyword và traffic thật.";

        private List<MetricItem> topKeywords = new ArrayList<>();
        private List<MetricItem> topPages = new ArrayList<>();
        private List<MetricItem> topCountriesList = new ArrayList<>();
    }

    @Data
    public static class PaidSearch {
        private Object paidKeywords = "N/A";
        private Object paidTraffic = "N/A";
        private Object paidCost = "N/A";
        private Object adsDetected = "N/A";

        private String dataSource = "N/A";
        private String note = "Cần API Semrush, Ahrefs hoặc DataForSEO để lấy paid keyword và ads data thật.";

        private List<MetricItem> topPaidKeywords = new ArrayList<>();
        private List<MetricItem> ads = new ArrayList<>();
    }

    @Data
    public static class SearchConsole {
        private Object clicks = "N/A";
        private Object impressions = "N/A";
        private Object ctr = "N/A";
        private Object averagePosition = "N/A";

        private String dataSource = "N/A";
        private String note = "Cần kết nối Google Search Console API và xác minh website để lấy dữ liệu thật.";

        private List<MetricItem> topQueries = new ArrayList<>();
        private List<MetricItem> topPages = new ArrayList<>();
    }

    @Data
    public static class MetricItem {
        private String label;
        private Object value;
        private String note;

        public MetricItem() {
        }

        public MetricItem(String label, Object value, String note) {
            this.label = label;
            this.value = value;
            this.note = note;
        }
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