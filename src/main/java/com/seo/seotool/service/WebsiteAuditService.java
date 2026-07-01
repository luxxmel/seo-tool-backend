package com.seo.seotool.service;

import com.seo.seotool.dto.AuditRequest;
import com.seo.seotool.dto.AuditResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebsiteAuditService {

    private static final int DEFAULT_MAX_URLS = 50;
    private static final int HARD_MAX_URLS = 1000;
    private static final int CONNECT_TIMEOUT = 12000;
    private static final int READ_TIMEOUT = 12000;
    private static final int MAX_HTML_CHARS = 400000;
    private static final long CRAWL_DELAY_MS = 500;

    private final ObjectProvider<DataForSeoBacklinkService> dataForSeoBacklinkServiceProvider;

    public WebsiteAuditService(ObjectProvider<DataForSeoBacklinkService> dataForSeoBacklinkServiceProvider) {
        this.dataForSeoBacklinkServiceProvider = dataForSeoBacklinkServiceProvider;
    }

    public AuditResponse scanWebsite(AuditRequest request) {
        AuditResponse response = new AuditResponse();

        String inputDomain = request == null ? "" : safeTrim(request.getDomain());
        int maxUrls = normalizeMaxUrls(request == null ? null : request.getMaxUrls());

        if (inputDomain.isEmpty()) {
            response.setStatus("ERROR");
            response.setMessage("Domain không được để trống.");
            applyExternalSeoPlaceholders(response);
            return response;
        }

        String normalizedDomain = normalizeDomain(inputDomain);
        response.setDomain(normalizedDomain);

        if (!isValidHttpUrl(normalizedDomain)) {
            response.setStatus("ERROR");
            response.setMessage("Domain sai định dạng. Ví dụ đúng: https://mecifactory.com");
            applyExternalSeoPlaceholders(response);
            return response;
        }

        try {
            SiteBasics basics = checkSiteBasics(normalizedDomain);
            response.setFinalUrl(basics.finalUrl);

            List<String> urls = discoverUrls(normalizedDomain, basics, maxUrls);

            if (urls.isEmpty()) {
                urls.add(normalizedDomain);
            }

            List<PageAuditInternal> pages = new ArrayList<>();

            for (String url : urls) {
                PageAuditInternal page = auditPage(url, normalizedDomain);
                pages.add(page);
                sleepQuietly(CRAWL_DELAY_MS);
            }

            fillResponse(response, basics, pages);
            applyExternalSeoData(response);

            response.setStatus("OK");
            response.setMessage("Audit hoàn tất lúc " + LocalTime.now().toString().substring(0, 8));

        } catch (Exception e) {
            response.setStatus("ERROR");
            response.setMessage("Audit lỗi: " + safeMessage(e.getMessage()));
            applyExternalSeoPlaceholders(response);
        }

        return response;
    }

    private SiteBasics checkSiteBasics(String domain) {
        SiteBasics basics = new SiteBasics();
        basics.inputUrl = domain;
        basics.finalUrl = domain;

        long start = System.currentTimeMillis();
        HttpResult home = fetchUrl(domain, true);
        basics.homeStatus = home.statusCode;
        basics.finalUrl = home.finalUrl == null || home.finalUrl.isBlank() ? domain : home.finalUrl;
        basics.homeResponseTimeMs = System.currentTimeMillis() - start;

        String root = getRootUrl(basics.finalUrl);

        HttpResult robots = fetchUrl(root + "/robots.txt", false);
        basics.robotsTxtFound = robots.statusCode >= 200 && robots.statusCode < 300;
        basics.robotsTxtContent = robots.body == null ? "" : robots.body;

        List<String> sitemapCandidates = findSitemapCandidates(root, basics.robotsTxtContent);

        for (String sitemapUrl : sitemapCandidates) {
            HttpResult sitemap = fetchUrl(sitemapUrl, false);

            if (sitemap.statusCode >= 200
                    && sitemap.statusCode < 300
                    && sitemap.body != null
                    && !sitemap.body.isBlank()) {
                basics.sitemapXmlFound = true;
                basics.sitemapUrl = sitemapUrl;
                basics.sitemapXmlContent = sitemap.body;
                break;
            }
        }

        return basics;
    }

    private List<String> discoverUrls(String domain, SiteBasics basics, int maxUrls) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();

        urls.add(domain);

        if (basics.sitemapXmlFound && basics.sitemapXmlContent != null) {
            List<String> sitemapUrls = extractUrlsFromSitemap(basics.sitemapXmlContent);

            for (String url : sitemapUrls) {
                if (isSameHost(domain, url)) {
                    urls.add(url);
                }

                if (urls.size() >= maxUrls) {
                    break;
                }
            }
        }

        return new ArrayList<>(urls);
    }

    private PageAuditInternal auditPage(String url, String rootDomain) {
        PageAuditInternal page = new PageAuditInternal();
        page.url = url;
        page.finalUrl = url;

        long start = System.currentTimeMillis();
        HttpResult result = fetchUrl(url, true);
        page.responseTimeMs = System.currentTimeMillis() - start;

        page.statusCode = result.statusCode;
        page.status = result.statusCode <= 0 ? "ERROR" : String.valueOf(result.statusCode);
        page.finalUrl = result.finalUrl == null || result.finalUrl.isBlank() ? url : result.finalUrl;
        page.html = result.body == null ? "" : result.body;
        page.contentType = result.contentType == null ? "" : result.contentType;

        page.alive = result.statusCode >= 200 && result.statusCode < 400;
        page.redirect = result.redirected || (result.statusCode >= 300 && result.statusCode < 400);
        page.blocked = isBlocked(result.statusCode, page.html);
        page.noindex = hasNoindex(page.html, result.xRobotsTag);
        page.nofollow = hasNofollow(page.html, result.xRobotsTag);

        page.indexability = calculateIndexability(page);

        page.title = extractTitle(page.html);
        page.description = extractMetaContent(page.html, "description");
        page.h1 = extractFirstH1(page.html);
        page.canonical = extractCanonical(page.html);

        page.images = countImages(page.html);
        page.missingAlt = countImagesMissingAlt(page.html);

        LinkCount linkCount = countLinks(page.html, rootDomain);
        page.internalLinks = linkCount.internalLinks;
        page.externalLinks = linkCount.externalLinks;

        page.issues = calculatePageIssues(page);

        return page;
    }

    private void fillResponse(AuditResponse response, SiteBasics basics, List<PageAuditInternal> pages) {
        int pagesScanned = pages.size();

        int errors = 0;
        int warnings = 0;

        int missingTitle = 0;
        int missingDescription = 0;
        int missingH1 = 0;
        int missingCanonical = 0;
        int noindexPages = 0;
        int blockedPages = 0;
        int brokenPages = 0;
        int redirects = 0;
        int missingAltImages = 0;
        int titleIssues = 0;
        int descriptionIssues = 0;
        int crawlablePages = 0;
        int totalResponseTime = 0;

        int indexablePages = 0;
        int notIndexablePages = 0;
        int unknownPages = 0;

        List<AuditResponse.AuditPageRow> rows = new ArrayList<>();

        for (PageAuditInternal page : pages) {
            totalResponseTime += (int) page.responseTimeMs;

            boolean pageIsBroken = page.statusCode == 404
                    || page.statusCode == 410
                    || page.statusCode >= 500
                    || page.statusCode <= 0;

            if ("INDEXABLE".equals(page.indexability)) {
                indexablePages++;
            } else if ("NOT_INDEXABLE".equals(page.indexability)) {
                notIndexablePages++;
            } else {
                unknownPages++;
            }

            if (page.alive && !page.blocked && !page.noindex) {
                crawlablePages++;
            }

            if (pageIsBroken) {
                brokenPages++;
                errors++;
            }

            if (page.blocked) {
                blockedPages++;
                warnings++;
            }

            if (page.redirect) {
                redirects++;
                warnings++;
            }

            if (page.noindex) {
                noindexPages++;
                warnings++;
            }

            if (page.title.isBlank()) {
                missingTitle++;
                titleIssues++;
                errors++;
            } else if (page.title.length() < 20 || page.title.length() > 65) {
                titleIssues++;
                warnings++;
            }

            if (page.description.isBlank()) {
                missingDescription++;
                descriptionIssues++;
                errors++;
            } else if (page.description.length() < 50 || page.description.length() > 170) {
                descriptionIssues++;
                warnings++;
            }

            if (page.h1.isBlank()) {
                missingH1++;
                warnings++;
            }

            if (page.canonical.isBlank()) {
                missingCanonical++;
                warnings++;
            }

            if (page.missingAlt > 0) {
                missingAltImages += page.missingAlt;
                warnings++;
            }

            rows.add(toPageRow(page));
        }

        int totalIssues = errors + warnings;
        int seoScore = calculateSeoScore(pagesScanned, errors, warnings, brokenPages, noindexPages, blockedPages);

        AuditResponse.AuditOverview overview = new AuditResponse.AuditOverview();
        overview.setSeoScore(seoScore);
        overview.setPagesScanned(pagesScanned);
        overview.setTotalIssues(totalIssues);
        overview.setErrors(errors);
        overview.setWarnings(warnings);
        response.setOverview(overview);

        AuditResponse.AuditTechnical technical = new AuditResponse.AuditTechnical();
        technical.setHealth(seoScore);
        technical.setHealthLabel(buildHealthLabel(seoScore));
        technical.setSpeed(calculateSpeedScore(totalResponseTime, pagesScanned));
        technical.setAvgResponseTime(buildAvgResponseTime(totalResponseTime, pagesScanned));
        technical.setCrawlablePages(crawlablePages);
        technical.setBlockedPages(blockedPages);
        technical.setNoindexPages(noindexPages);
        technical.setBrokenLinks(brokenPages);
        technical.setInternalBrokenLinks(brokenPages);
        technical.setRedirects(redirects);
        technical.setHttps(response.getFinalUrl() != null && response.getFinalUrl().startsWith("https://") ? "OK" : "N/A");
        technical.setRobotsTxt(basics.robotsTxtFound ? "Found" : "N/A");
        technical.setSitemapXml(basics.sitemapXmlFound ? "Found" : "N/A");
        response.setTechnical(technical);

        AuditResponse.AuditSearch search = new AuditResponse.AuditSearch();
        search.setIndexedPages(String.valueOf(indexablePages));
        search.setTopPages("Indexable: " + indexablePages);
        search.setNotIndexedPages(String.valueOf(notIndexablePages));
        search.setUnknownPages(String.valueOf(unknownPages));
        search.setTitleIssues(titleIssues);
        search.setDescriptionIssues(descriptionIssues);
        response.setSearch(search);

        List<AuditResponse.AuditIssueSummary> issueSummary = new ArrayList<>();

        if (missingTitle > 0) {
            issueSummary.add(new AuditResponse.AuditIssueSummary("Missing title", missingTitle, missingTitle, "mdi-google", "google-icon"));
        }

        if (missingDescription > 0) {
            issueSummary.add(new AuditResponse.AuditIssueSummary("Missing description", missingDescription, missingDescription, "mdi-text-box-outline", "blue-icon"));
        }

        if (missingH1 > 0) {
            issueSummary.add(new AuditResponse.AuditIssueSummary("Missing H1", missingH1, missingH1, "mdi-format-header-1", "cyan-icon"));
        }

        if (noindexPages > 0) {
            issueSummary.add(new AuditResponse.AuditIssueSummary("Noindex pages", noindexPages, noindexPages, "mdi-robot-confused-outline", "orange-icon"));
        }

        if (brokenPages > 0) {
            issueSummary.add(new AuditResponse.AuditIssueSummary("Broken pages", brokenPages, brokenPages, "mdi-link-off", "red-icon"));
        }

        if (missingCanonical > 0) {
            issueSummary.add(new AuditResponse.AuditIssueSummary("Missing canonical", missingCanonical, missingCanonical, "mdi-link-variant-off", "orange-icon"));
        }

        if (missingAltImages > 0) {
            issueSummary.add(new AuditResponse.AuditIssueSummary("Images missing alt", missingAltImages, missingAltImages, "mdi-image-off-outline", "blue-icon"));
        }

        response.setIssueSummary(issueSummary);
        response.setPageRows(rows);
    }

    private String calculateIndexability(PageAuditInternal page) {
        boolean broken = page.statusCode == 404
                || page.statusCode == 410
                || page.statusCode >= 500
                || page.statusCode <= 0;

        if (broken || page.blocked || page.noindex) {
            return "NOT_INDEXABLE";
        }

        if (page.statusCode >= 200 && page.statusCode < 300) {
            return "INDEXABLE";
        }

        if (page.statusCode >= 300 && page.statusCode < 400) {
            return "UNKNOWN";
        }

        return "UNKNOWN";
    }

    private void applyExternalSeoData(AuditResponse response) {
        applyExternalSeoPlaceholders(response);

        try {
            DataForSeoBacklinkService dataForSeoBacklinkService =
                    dataForSeoBacklinkServiceProvider.getIfAvailable();

            if (dataForSeoBacklinkService == null) {
                AuditResponse.BacklinkProfile backlink = response.getBacklink();
                backlink.setDataSource("Not connected");
                backlink.setNote("Chưa có DataForSeoBacklinkService hoặc service chưa khởi tạo được.");
                response.setBacklink(backlink);
                return;
            }

            String target = response.getFinalUrl() != null && !response.getFinalUrl().isBlank()
                    ? response.getFinalUrl()
                    : response.getDomain();

            AuditResponse.BacklinkProfile backlinkProfile =
                    dataForSeoBacklinkService.fetchBacklinkProfile(target);

            if (backlinkProfile != null) {
                response.setBacklink(backlinkProfile);
            }
        } catch (Exception e) {
            AuditResponse.BacklinkProfile backlink = new AuditResponse.BacklinkProfile();
            backlink.setAuthorityScore("N/A");
            backlink.setDomainRating("N/A");
            backlink.setUrlRating("N/A");
            backlink.setBacklinks("N/A");
            backlink.setReferringDomains("N/A");
            backlink.setDofollowBacklinks("N/A");
            backlink.setNofollowBacklinks("N/A");
            backlink.setNewBacklinks("N/A");
            backlink.setLostBacklinks("N/A");
            backlink.setDataSource("Backlink API error");
            backlink.setNote("Lỗi khi gọi DataForSEO: " + safeMessage(e.getMessage()));
            response.setBacklink(backlink);
        }
    }

    private void applyExternalSeoPlaceholders(AuditResponse response) {
        AuditResponse.BacklinkProfile backlink = new AuditResponse.BacklinkProfile();
        backlink.setAuthorityScore("N/A");
        backlink.setDomainRating("N/A");
        backlink.setUrlRating("N/A");
        backlink.setBacklinks("N/A");
        backlink.setReferringDomains("N/A");
        backlink.setDofollowBacklinks("N/A");
        backlink.setNofollowBacklinks("N/A");
        backlink.setNewBacklinks("N/A");
        backlink.setLostBacklinks("N/A");
        backlink.setDataSource("Not connected");
        backlink.setNote("Cần kết nối API Ahrefs, Semrush, Moz, Majestic hoặc DataForSEO để lấy backlink/ref.domain thật.");
        response.setBacklink(backlink);

        AuditResponse.OrganicSearch organic = new AuditResponse.OrganicSearch();
        organic.setOrganicKeywords("N/A");
        organic.setOrganicTraffic("N/A");
        organic.setTrafficValue("N/A");
        organic.setTopCountries("N/A");
        organic.setDataSource("Not connected");
        organic.setNote("Cần kết nối Google Search Console, Ahrefs, Semrush hoặc DataForSEO để lấy organic keyword/traffic thật.");
        response.setOrganic(organic);

        AuditResponse.PaidSearch paid = new AuditResponse.PaidSearch();
        paid.setPaidKeywords("N/A");
        paid.setPaidTraffic("N/A");
        paid.setPaidCost("N/A");
        paid.setAdsDetected("N/A");
        paid.setDataSource("Not connected");
        paid.setNote("Cần API Semrush, Ahrefs hoặc DataForSEO để lấy paid keyword/ads thật.");
        response.setPaid(paid);

        AuditResponse.SearchConsole searchConsole = new AuditResponse.SearchConsole();
        searchConsole.setClicks("N/A");
        searchConsole.setImpressions("N/A");
        searchConsole.setCtr("N/A");
        searchConsole.setAveragePosition("N/A");
        searchConsole.setDataSource("Not connected");
        searchConsole.setNote("Cần kết nối Google Search Console API để lấy click, impression, CTR và vị trí trung bình thật.");
        response.setSearchConsole(searchConsole);
    }

    private AuditResponse.AuditPageRow toPageRow(PageAuditInternal page) {
        AuditResponse.AuditPageRow row = new AuditResponse.AuditPageRow();

        row.setUrl(page.url);
        row.setFinalUrl(page.finalUrl);
        row.setStatus(page.status);
        row.setStatusClass(buildStatusClass(page.statusCode, page.blocked));
        row.setIndex(buildIndexValue(page));

        row.setTitle(page.title.isBlank() ? "Missing" : "OK");
        row.setDescription(page.description.isBlank() ? "Missing" : "OK");
        row.setH1(page.h1.isBlank() ? "Missing" : "OK");
        row.setIssues(page.issues);

        row.setCanonical(page.canonical.isBlank() ? "Missing" : page.canonical);
        row.setRobots(buildRobotsValue(page));
        row.setInternalLinks(page.internalLinks);
        row.setExternalLinks(page.externalLinks);
        row.setImages(page.images);
        row.setMissingAlt(page.missingAlt);
        row.setResponseTimeMs(page.responseTimeMs);
        row.setNote(buildPageNote(page));

        return row;
    }

    private String buildIndexValue(PageAuditInternal page) {
        if ("INDEXABLE".equals(page.indexability)) {
            return "Indexable";
        }

        if ("NOT_INDEXABLE".equals(page.indexability)) {
            return "Not indexable";
        }

        return "Unknown";
    }

    private int calculatePageIssues(PageAuditInternal page) {
        int issues = 0;

        if (page.statusCode == 404 || page.statusCode == 410 || page.statusCode >= 500 || page.statusCode <= 0) {
            issues++;
        }

        if (page.blocked) {
            issues++;
        }

        if (page.noindex) {
            issues++;
        }

        if (page.title.isBlank()) {
            issues++;
        }

        if (page.description.isBlank()) {
            issues++;
        }

        if (page.h1.isBlank()) {
            issues++;
        }

        if (page.canonical.isBlank()) {
            issues++;
        }

        if (page.missingAlt > 0) {
            issues++;
        }

        return issues;
    }

    private int calculateSeoScore(int pagesScanned, int errors, int warnings, int brokenPages, int noindexPages, int blockedPages) {
        if (pagesScanned <= 0) {
            return 0;
        }

        int score = 100;

        score -= errors * 6;
        score -= warnings * 2;
        score -= brokenPages * 8;
        score -= noindexPages * 4;
        score -= blockedPages * 4;

        return Math.max(0, Math.min(100, score));
    }

    private int calculateSpeedScore(int totalResponseTime, int pagesScanned) {
        if (pagesScanned <= 0) {
            return 0;
        }

        int avg = totalResponseTime / pagesScanned;

        if (avg <= 500) return 95;
        if (avg <= 1000) return 85;
        if (avg <= 2000) return 70;
        if (avg <= 3500) return 55;

        return 35;
    }

    private String buildAvgResponseTime(int totalResponseTime, int pagesScanned) {
        if (pagesScanned <= 0) {
            return "N/A";
        }

        return "Avg " + (totalResponseTime / pagesScanned) + "ms";
    }

    private String buildHealthLabel(int seoScore) {
        if (seoScore >= 85) return "Good";
        if (seoScore >= 65) return "Medium";
        return "Need fix";
    }

    private String buildStatusClass(int statusCode, boolean blocked) {
        if (blocked) return "warning";
        if (statusCode >= 200 && statusCode < 400) return "ok";
        if (statusCode >= 400) return "error";
        return "warning";
    }

    private String buildRobotsValue(PageAuditInternal page) {
        if (page.noindex && page.nofollow) return "noindex,nofollow";
        if (page.noindex) return "noindex";
        if (page.nofollow) return "nofollow";
        return "OK";
    }

    private String buildPageNote(PageAuditInternal page) {
        if (page.statusCode == 404 || page.statusCode == 410) {
            return "URL lỗi 404/410.";
        }

        if (page.statusCode >= 500) {
            return "Server lỗi khi crawl URL.";
        }

        if (page.blocked) {
            return "URL có dấu hiệu chặn bot/captcha/firewall.";
        }

        if (page.noindex) {
            return "URL đang có noindex.";
        }

        if ("INDEXABLE".equals(page.indexability)) {
            return "URL có thể index. Muốn biết đã index thật trên Google cần Google Search Console API.";
        }

        if (page.issues > 0) {
            return "Có lỗi SEO cần kiểm tra.";
        }

        return "OK";
    }

    private HttpResult fetchUrl(String url, boolean readBody) {
        HttpURLConnection connection = null;
        HttpResult result = new HttpResult();
        result.finalUrl = url;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getUserAgent());
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Connection", "close");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();

            result.statusCode = statusCode;
            result.finalUrl = connection.getURL().toString();
            result.redirected = !normalizeUrlForCompare(url).equals(normalizeUrlForCompare(result.finalUrl));
            result.contentType = connection.getHeaderField("Content-Type");
            result.xRobotsTag = connection.getHeaderField("X-Robots-Tag");

            if (readBody || isLikelyTextResponse(result.contentType)) {
                result.body = readResponseBody(connection, statusCode);
            } else {
                result.body = "";
            }

        } catch (Exception e) {
            result.statusCode = -1;
            result.body = "";
            result.error = e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return result;
    }

    private String readResponseBody(HttpURLConnection connection, int statusCode) {
        try {
            InputStream stream = statusCode >= 200 && statusCode < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                return "";
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

            StringBuilder body = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null && body.length() < MAX_HTML_CHARS) {
                body.append(line).append("\n");
            }

            reader.close();

            return body.toString();

        } catch (Exception e) {
            return "";
        }
    }

    private boolean isLikelyTextResponse(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }

        String lower = contentType.toLowerCase();

        return lower.contains("text")
                || lower.contains("html")
                || lower.contains("xml")
                || lower.contains("json");
    }

    private List<String> findSitemapCandidates(String root, String robotsTxt) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        if (robotsTxt != null && !robotsTxt.isBlank()) {
            Matcher matcher = Pattern.compile("(?im)^\\s*Sitemap:\\s*(\\S+)\\s*$").matcher(robotsTxt);

            while (matcher.find()) {
                String sitemapUrl = matcher.group(1).trim();

                if (isValidHttpUrl(sitemapUrl)) {
                    candidates.add(sitemapUrl);
                }
            }
        }

        candidates.add(root + "/sitemap.xml");
        candidates.add(root + "/sitemap_index.xml");
        candidates.add(root + "/wp-sitemap.xml");

        return new ArrayList<>(candidates);
    }

    private List<String> extractUrlsFromSitemap(String xml) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();

        if (xml == null || xml.isBlank()) {
            return new ArrayList<>(urls);
        }

        Matcher locMatcher = Pattern.compile("(?is)<loc>\\s*(.*?)\\s*</loc>").matcher(xml);

        while (locMatcher.find()) {
            String loc = decodeXml(locMatcher.group(1).trim());

            if (isValidHttpUrl(loc)) {
                urls.add(loc);
            }
        }

        return new ArrayList<>(urls);
    }

    private String extractTitle(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Matcher matcher = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);

        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }

        return "";
    }

    private String extractMetaContent(String html, String name) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Pattern pattern1 = Pattern.compile(
                "(?is)<meta[^>]*name=[\"']" + Pattern.quote(name) + "[\"'][^>]*content=[\"'](.*?)[\"'][^>]*>"
        );
        Matcher matcher1 = pattern1.matcher(html);

        if (matcher1.find()) {
            return cleanText(matcher1.group(1));
        }

        Pattern pattern2 = Pattern.compile(
                "(?is)<meta[^>]*content=[\"'](.*?)[\"'][^>]*name=[\"']" + Pattern.quote(name) + "[\"'][^>]*>"
        );
        Matcher matcher2 = pattern2.matcher(html);

        if (matcher2.find()) {
            return cleanText(matcher2.group(1));
        }

        return "";
    }

    private String extractFirstH1(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Matcher matcher = Pattern.compile("(?is)<h1[^>]*>(.*?)</h1>").matcher(html);

        if (matcher.find()) {
            return cleanText(stripTags(matcher.group(1)));
        }

        return "";
    }

    private String extractCanonical(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Pattern pattern1 = Pattern.compile(
                "(?is)<link[^>]*rel=[\"']canonical[\"'][^>]*href=[\"'](.*?)[\"'][^>]*>"
        );
        Matcher matcher1 = pattern1.matcher(html);

        if (matcher1.find()) {
            return matcher1.group(1).trim();
        }

        Pattern pattern2 = Pattern.compile(
                "(?is)<link[^>]*href=[\"'](.*?)[\"'][^>]*rel=[\"']canonical[\"'][^>]*>"
        );
        Matcher matcher2 = pattern2.matcher(html);

        if (matcher2.find()) {
            return matcher2.group(1).trim();
        }

        return "";
    }

    private boolean hasNoindex(String html, String xRobotsTag) {
        if (xRobotsTag != null && xRobotsTag.toLowerCase().contains("noindex")) {
            return true;
        }

        String robots = extractMetaContent(html, "robots").toLowerCase();
        String googlebot = extractMetaContent(html, "googlebot").toLowerCase();

        return robots.contains("noindex") || googlebot.contains("noindex");
    }

    private boolean hasNofollow(String html, String xRobotsTag) {
        if (xRobotsTag != null && xRobotsTag.toLowerCase().contains("nofollow")) {
            return true;
        }

        String robots = extractMetaContent(html, "robots").toLowerCase();
        String googlebot = extractMetaContent(html, "googlebot").toLowerCase();

        return robots.contains("nofollow") || googlebot.contains("nofollow");
    }

    private boolean isBlocked(int statusCode, String html) {
        if (statusCode == 403 || statusCode == 429 || statusCode == 503) {
            return true;
        }

        if (html == null || html.isBlank()) {
            return false;
        }

        String lower = html.toLowerCase();

        return lower.contains("captcha")
                || lower.contains("access denied")
                || lower.contains("forbidden")
                || lower.contains("cloudflare")
                || lower.contains("checking your browser")
                || lower.contains("verify you are human")
                || lower.contains("temporarily blocked")
                || lower.contains("too many requests");
    }

    private int countImages(String html) {
        if (html == null || html.isBlank()) {
            return 0;
        }

        return countMatches(html, "(?is)<img\\b[^>]*>");
    }

    private int countImagesMissingAlt(String html) {
        if (html == null || html.isBlank()) {
            return 0;
        }

        int missing = 0;
        Matcher matcher = Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);

        while (matcher.find()) {
            String img = matcher.group();

            Matcher altMatcher = Pattern.compile("(?is)\\salt\\s*=\\s*([\"'])(.*?)\\1").matcher(img);

            if (!altMatcher.find() || altMatcher.group(2).trim().isBlank()) {
                missing++;
            }
        }

        return missing;
    }

    private LinkCount countLinks(String html, String rootDomain) {
        LinkCount count = new LinkCount();

        if (html == null || html.isBlank()) {
            return count;
        }

        String rootHost = getHostQuietly(rootDomain);

        Matcher matcher = Pattern.compile("(?is)<a\\b[^>]*href=[\"'](.*?)[\"'][^>]*>").matcher(html);

        while (matcher.find()) {
            String href = matcher.group(1).trim();

            if (href.isBlank()
                    || href.startsWith("#")
                    || href.startsWith("mailto:")
                    || href.startsWith("tel:")
                    || href.startsWith("javascript:")) {
                continue;
            }

            if (href.startsWith("/")) {
                count.internalLinks++;
                continue;
            }

            if (isValidHttpUrl(href)) {
                String host = getHostQuietly(href);

                if (!rootHost.isBlank() && host.equals(rootHost)) {
                    count.internalLinks++;
                } else {
                    count.externalLinks++;
                }
            }
        }

        return count;
    }

    private int countMatches(String text, String regex) {
        int count = 0;
        Matcher matcher = Pattern.compile(regex).matcher(text);

        while (matcher.find()) {
            count++;
        }

        return count;
    }

    private String normalizeDomain(String raw) {
        String value = safeTrim(raw);

        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }

        value = value.replaceAll("\\s+", "");

        return value;
    }

    private int normalizeMaxUrls(Integer maxUrls) {
        if (maxUrls == null || maxUrls <= 0) {
            return DEFAULT_MAX_URLS;
        }

        return Math.min(maxUrls, HARD_MAX_URLS);
    }

    private String getRootUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                return url;
            }

            int port = uri.getPort();

            if (port > 0) {
                return scheme + "://" + host + ":" + port;
            }

            return scheme + "://" + host;

        } catch (Exception e) {
            return url;
        }
    }

    private boolean isSameHost(String baseUrl, String targetUrl) {
        String baseHost = getHostQuietly(baseUrl);
        String targetHost = getHostQuietly(targetUrl);

        return !baseHost.isBlank() && baseHost.equals(targetHost);
    }

    private String getHostQuietly(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            return host == null ? "" : host.toLowerCase();

        } catch (Exception e) {
            return "";
        }
    }

    private boolean isValidHttpUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();

            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeUrlForCompare(String url) {
        if (url == null) {
            return "";
        }

        return url.toLowerCase()
                .replace("&amp;", "&")
                .replace("https://", "")
                .replace("http://", "")
                .replace("www.", "")
                .replaceAll("#.*$", "")
                .replaceAll("\\?.*$", "")
                .replaceAll("/$", "")
                .trim();
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }

        return decodeHtml(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripTags(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("(?is)<[^>]+>", " ");
    }

    private String decodeXml(String value) {
        return decodeHtml(value);
    }

    private String decodeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Không rõ lỗi.";
        }

        if (message.length() > 180) {
            return message.substring(0, 180) + "...";
        }

        return message;
    }

    private String getUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignored) {
        }
    }

    private static class SiteBasics {
        String inputUrl;
        String finalUrl;
        int homeStatus;
        long homeResponseTimeMs;
        boolean robotsTxtFound;
        boolean sitemapXmlFound;
        String robotsTxtContent;
        String sitemapXmlContent;
        String sitemapUrl;
    }

    private static class HttpResult {
        int statusCode;
        String finalUrl;
        String body;
        String contentType;
        String xRobotsTag;
        String error;
        boolean redirected;
    }

    private static class PageAuditInternal {
        String url;
        String finalUrl;
        String status;
        String html;
        String contentType;

        int statusCode;
        boolean alive;
        boolean redirect;
        boolean blocked;
        boolean noindex;
        boolean nofollow;

        String indexability = "UNKNOWN";

        String title = "";
        String description = "";
        String h1 = "";
        String canonical = "";

        int images;
        int missingAlt;
        int internalLinks;
        int externalLinks;
        int issues;

        long responseTimeMs;
    }

    private static class LinkCount {
        int internalLinks;
        int externalLinks;
    }
}