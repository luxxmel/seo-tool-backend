package com.seo.seotool.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeoService {

    private static final int MAX_INDEX_HUB_URLS = 1500;

    /*
     * Lưu URL đã ping vào RAM.
     * Lưu ý: Render restart/redeploy thì dữ liệu RAM có thể mất.
     * Sau này muốn bền hơn thì chuyển sang PostgreSQL/NocoDB/file storage.
     */
    private final LinkedHashSet<String> indexHubUrls = new LinkedHashSet<>();

    /*
     * Legacy endpoint.
     * Nếu controller cũ còn gọi /ping thì vẫn compile.
     * Không dùng GSC nữa, chuyển thành Fast Ping.
     */
    public String processPing(List<String> urls) {
        List<Map<String, Object>> result = processDirectPing(urls);

        int pinged = 0;
        int invalid = 0;
        int attempted = 0;

        StringBuilder log = new StringBuilder();

        for (Map<String, Object> item : result) {
            String discoveryStatus = String.valueOf(item.getOrDefault("discoveryStatus", ""));
            String url = String.valueOf(item.getOrDefault("url", ""));
            String httpStatus = String.valueOf(item.getOrDefault("httpStatus", ""));
            String message = String.valueOf(item.getOrDefault("message", ""));

            if ("PING_SENT".equals(discoveryStatus)) {
                pinged++;
            } else if ("INVALID_URL".equals(discoveryStatus)) {
                invalid++;
            } else {
                attempted++;
            }

            log.append(discoveryStatus)
                    .append(" | HTTP ")
                    .append(httpStatus)
                    .append(" | ")
                    .append(url)
                    .append(" | ")
                    .append(message)
                    .append("\n");
        }

        return String.format(
                "Fast Ping hoàn tất: Pinged: %d | Attempted: %d | Invalid: %d%n%s",
                pinged,
                attempted,
                invalid,
                log
        );
    }

    /*
     * Fast Ping + Auto Index Hub
     *
     * Logic:
     * - URL hợp lệ là ping thẳng.
     * - Không check alive trước.
     * - Không check noindex.
     * - Không đọc HTML.
     * - Không cần final URL.
     * - Ping xong tự thêm URL vào Index Hub để bot có đường crawl.
     */
    public List<Map<String, Object>> processDirectPing(List<String> urls) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (urls == null || urls.isEmpty()) {
            return result;
        }

        Set<String> cleanUrls = new LinkedHashSet<>();

        for (String rawUrl : urls) {
            String url = cleanUrl(rawUrl);

            if (!url.isEmpty()) {
                cleanUrls.add(url);
            }
        }

        for (String url : cleanUrls) {
            Map<String, Object> item = new HashMap<>();
            item.put("url", url);
            item.put("time", LocalTime.now().toString().substring(0, 8));

            if (!isValidHttpUrl(url)) {
                item.put("httpStatus", "INVALID");
                item.put("pingStatus", "failed");
                item.put("discoveryStatus", "INVALID_URL");
                item.put("method", "SKIPPED");
                item.put("addedToHub", false);
                item.put("message", "URL sai định dạng. Chỉ nhận http:// hoặc https://.");

                result.add(item);
                continue;
            }

            FastPingResult ping = fastPingUrl(url);

            addUrlToIndexHub(url);

            item.put("httpStatus", ping.httpStatus);
            item.put("pingStatus", ping.success ? "sent" : "attempted");
            item.put("discoveryStatus", ping.success ? "PING_SENT" : "PING_ATTEMPTED");
            item.put("method", "FAST_DIRECT");
            item.put("addedToHub", true);
            item.put("message", buildFastPingMessage(ping) + " URL đã được thêm vào Index Hub.");

            result.add(item);

            sleepQuietly(80);
        }

        return result;
    }

    private FastPingResult fastPingUrl(String url) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getUserAgent());
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();

            /*
             * Không đọc HTML.
             * Chỉ lấy HTTP status để biết request đã được gửi tới server.
             */
            return new FastPingResult(
                    true,
                    String.valueOf(statusCode),
                    ""
            );

        } catch (Exception e) {
            /*
             * ERROR vẫn là attempted vì backend đã cố gửi request.
             */
            return new FastPingResult(
                    false,
                    "ERROR",
                    e.getMessage()
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildFastPingMessage(FastPingResult ping) {
        if (ping.success) {
            return "Đã ping thẳng URL. Không check HTML, không check noindex, không lọc sống/chết.";
        }

        return "Đã thử ping URL nhưng backend gặp lỗi kết nối: " + safeMessage(ping.error);
    }

    /*
     * Audit URL
     * Chỉ dùng khi m muốn kiểm tra sống/chết/noindex/blocked.
     * Không liên quan tới Fast Ping.
     */
    public List<Map<String, Object>> processAudit(List<String> urls) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (urls == null || urls.isEmpty()) {
            return result;
        }

        for (String rawUrl : urls) {
            String url = cleanUrl(rawUrl);

            if (url.isEmpty()) {
                continue;
            }

            UrlCheckResult checked = checkUrl(url);

            Map<String, Object> item = new HashMap<>();
            item.put("url", checked.originalUrl);
            item.put("finalUrl", checked.finalUrl);
            item.put("status", checked.httpStatus);
            item.put("alive", checked.alive);
            item.put("title", checked.title);
            item.put("noindex", checked.noindex);
            item.put("blocked", checked.blocked);
            item.put("loginWall", checked.loginWall);
            item.put("canSubmit", checked.canSubmit());
            item.put("note", checked.canSubmit() ? "URL OK, có thể crawl/discovery." : checked.getSkipReason());

            result.add(item);
        }

        return result;
    }

    /*
     * Check Index bằng chính URL user nhập.
     * Đây là check tương đối bằng Google Search HTML, có thể bị Google block.
     */
    public List<Map<String, Object>> processCheckIndex(List<String> urls) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (urls == null || urls.isEmpty()) {
            return result;
        }

        for (String rawUrl : urls) {
            String url = cleanUrl(rawUrl);

            if (url.isEmpty()) {
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("url", url);
            item.put("checkedAt", LocalTime.now().toString().substring(0, 8));

            if (!isValidHttpUrl(url)) {
                item.put("finalUrl", url);
                item.put("httpStatus", "INVALID");
                item.put("alive", false);
                item.put("title", "");
                item.put("noindex", false);
                item.put("blocked", false);
                item.put("loginWall", false);
                item.put("indexed", null);
                item.put("indexStatus", "ERROR");
                item.put("note", "Invalid URL. Index check could not be completed.");

                result.add(item);
                sleepQuietly(300);
                continue;
            }

            UrlCheckResult checked = checkUrl(url);
            GoogleIndexResult googleIndex = checkGoogleIndexedByInputUrl(url, checked.finalUrl);

            item.put("url", checked.originalUrl);
            item.put("finalUrl", checked.finalUrl);
            item.put("httpStatus", checked.httpStatus);
            item.put("alive", checked.alive);
            item.put("title", checked.title);
            item.put("noindex", checked.noindex);
            item.put("blocked", checked.blocked);
            item.put("loginWall", checked.loginWall);
            item.put("indexed", googleIndex.indexed);
            item.put("indexStatus", googleIndex.status);

            String note;

            if ("GOOGLE_BLOCK".equals(googleIndex.status)) {
                note = "Google blocked the automated search request. Index status is unknown.";
            } else if ("INDEXED".equals(googleIndex.status)) {
                note = googleIndex.note != null && !googleIndex.note.isEmpty()
                        ? googleIndex.note
                        : "URL was found in Google organic results.";

                if (checked.noindex) {
                    note += " Warning: the URL currently has noindex.";
                } else if (checked.blocked) {
                    note += " Warning: the server currently blocks automated access.";
                } else if (!checked.alive) {
                    note += " Warning: the URL does not currently return HTTP 2xx/3xx.";
                }
            } else if ("NOT_INDEXED".equals(googleIndex.status)) {
                if (checked.noindex) {
                    note = "Not found in Google. The URL currently has noindex.";
                } else if (!checked.alive) {
                    note = "Not found in Google. The URL currently has HTTP or access issues.";
                } else if (checked.blocked) {
                    note = "Not found in Google. The URL shows server blocking signals.";
                } else if (checked.loginWall) {
                    note = "Not found in Google. The URL may be behind a login wall.";
                } else {
                    note = "URL was not found in Google organic results.";
                }
            } else {
                note = googleIndex.note;
            }

            item.put("note", note);

            result.add(item);

            sleepQuietly(1200);
        }

        return result;
    }

    /*
     * Index Hub
     * Đây là tầng phát hiện URL cho bot.
     */
    private synchronized void addUrlToIndexHub(String url) {
        if (!isValidHttpUrl(url)) {
            return;
        }

        /*
         * URL mới hoặc vừa ping lại sẽ được đẩy lên đầu danh sách.
         */
        indexHubUrls.remove(url);
        indexHubUrls.add(url);

        while (indexHubUrls.size() > MAX_INDEX_HUB_URLS) {
            String firstUrl = indexHubUrls.iterator().next();
            indexHubUrls.remove(firstUrl);
        }
    }

    public synchronized List<String> getIndexHubUrls() {
        List<String> urls = new ArrayList<>(indexHubUrls);
        Collections.reverse(urls);
        return urls;
    }

    public synchronized String renderIndexHubHtml() {
        List<String> urls = getIndexHubUrls();
        String updatedAt = java.time.LocalDateTime.now().toString();

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html>\n");
        html.append("<html lang=\"vi\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"utf-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        html.append("  <meta name=\"robots\" content=\"index, follow\">\n");
        html.append("  <title>MECI Index Hub - URL Discovery</title>\n");
        html.append("  <meta name=\"description\" content=\"Public URL discovery hub for latest MECI links, social profiles, posts and industrial solution pages.\">\n");
        html.append("  <style>\n");
        html.append("    body{font-family:Arial,sans-serif;max-width:1040px;margin:40px auto;padding:0 18px;line-height:1.6;color:#111827;background:#f8fafc;}\n");
        html.append("    h1{font-size:34px;margin-bottom:8px;color:#0f172a;}\n");
        html.append("    h2{font-size:22px;margin-top:26px;color:#0f172a;}\n");
        html.append("    .meta{color:#64748b;margin-bottom:24px;}\n");
        html.append("    .tools{background:#eef6ff;border:1px solid #bfdbfe;border-radius:16px;padding:14px 16px;margin-bottom:20px;}\n");
        html.append("    .card{background:#fff;border:1px solid #e5e7eb;border-radius:16px;padding:16px;margin-bottom:10px;box-shadow:0 8px 24px rgba(15,23,42,.06);}\n");
        html.append("    a{color:#0369a1;text-decoration:none;word-break:break-all;font-weight:700;}\n");
        html.append("    a:hover{text-decoration:underline;}\n");
        html.append("    .small{font-size:13px;color:#64748b;}\n");
        html.append("    .count{display:inline-block;background:#dcfce7;color:#166534;border:1px solid #bbf7d0;border-radius:999px;padding:5px 10px;font-size:13px;font-weight:700;}\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <h1>MECI Index Hub</h1>\n");
        html.append("  <p class=\"meta\">Latest URLs submitted for public discovery. Updated at ")
                .append(escapeHtml(updatedAt))
                .append("</p>\n");

        html.append("  <p><span class=\"count\">")
                .append(urls.size())
                .append(" URLs in hub</span></p>\n");

        html.append("  <div class=\"tools small\">\n");
        html.append("    Sitemap: <a href=\"/api/seo/index-hub/sitemap.xml\">/api/seo/index-hub/sitemap.xml</a><br>\n");
        html.append("    RSS: <a href=\"/api/seo/index-hub/rss.xml\">/api/seo/index-hub/rss.xml</a>\n");
        html.append("  </div>\n");

        if (urls.isEmpty()) {
            html.append("  <div class=\"card\">No URLs submitted yet.</div>\n");
        } else {
            html.append("  <h2>Latest Submitted URLs</h2>\n");

            for (String url : urls) {
                html.append("  <div class=\"card\">\n");
                html.append("    <a href=\"")
                        .append(escapeHtml(url))
                        .append("\" rel=\"noopener\" target=\"_blank\">")
                        .append(escapeHtml(url))
                        .append("</a>\n");
                html.append("  </div>\n");
            }
        }

        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    public synchronized String renderIndexHubSitemapXml() {
        List<String> urls = getIndexHubUrls();
        String today = java.time.LocalDate.now().toString();

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        xml.append("  <url>\n");
        xml.append("    <loc>https://seo-tool-backend-phw2.onrender.com/api/seo/index-hub</loc>\n");
        xml.append("    <lastmod>").append(today).append("</lastmod>\n");
        xml.append("  </url>\n");

        for (String url : urls) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(url)).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("  </url>\n");
        }

        xml.append("</urlset>");

        return xml.toString();
    }

    public synchronized String renderIndexHubRssXml() {
        List<String> urls = getIndexHubUrls();
        String buildDate = java.time.ZonedDateTime.now().toString();

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\">\n");
        xml.append("<channel>\n");
        xml.append("  <title>MECI Index Hub Feed</title>\n");
        xml.append("  <link>https://seo-tool-backend-phw2.onrender.com/api/seo/index-hub</link>\n");
        xml.append("  <description>Latest URLs submitted to MECI discovery hub.</description>\n");
        xml.append("  <lastBuildDate>").append(escapeXml(buildDate)).append("</lastBuildDate>\n");

        for (String url : urls) {
            xml.append("  <item>\n");
            xml.append("    <title>").append(escapeXml(url)).append("</title>\n");
            xml.append("    <link>").append(escapeXml(url)).append("</link>\n");
            xml.append("    <guid>").append(escapeXml(url)).append("</guid>\n");
            xml.append("  </item>\n");
        }

        xml.append("</channel>\n");
        xml.append("</rss>");

        return xml.toString();
    }

    /*
     * Google index check helpers
     */
    private GoogleIndexResult checkGoogleIndexedByInputUrl(String inputUrl, String finalUrl) {
        List<String> queries = buildGoogleIndexQueriesFromInputUrl(inputUrl);

        boolean googleSearchWorked = false;

        for (String query : queries) {
            GoogleSearchResult searchResult = runGoogleSearch(query);

            if ("GOOGLE_BLOCK".equals(searchResult.status)) {
                return new GoogleIndexResult(
                        null,
                        "GOOGLE_BLOCK",
                        "Google blocked the automated search request."
                );
            }

            if ("ERROR".equals(searchResult.status)) {
                continue;
            }

            googleSearchWorked = true;

            if (matchesGoogleResults(inputUrl, finalUrl, searchResult.resultUrls)) {
                return new GoogleIndexResult(
                        true,
                        "INDEXED",
                        buildIndexedNote(inputUrl)
                );
            }
        }

        if (!googleSearchWorked) {
            return new GoogleIndexResult(
                    null,
                    "ERROR",
                    "Index check error: Google search could not be completed."
            );
        }

        return new GoogleIndexResult(
                false,
                "NOT_INDEXED",
                "URL was not found in Google organic results."
        );
    }

    private List<String> buildGoogleIndexQueriesFromInputUrl(String inputUrl) {
        Set<String> queries = new LinkedHashSet<>();

        queries.add("site:" + inputUrl);
        queries.add("\"" + inputUrl + "\"");

        String googleFileId = extractGoogleFileId(inputUrl);

        if (googleFileId != null && !googleFileId.isEmpty()) {
            queries.add("inurl:" + googleFileId);

            String host = getHostQuietly(inputUrl);

            if (host.contains("docs.google.com")) {
                if (inputUrl.contains("/document/")) {
                    queries.add("site:docs.google.com/document/d/ " + googleFileId);
                } else if (inputUrl.contains("/spreadsheets/")) {
                    queries.add("site:docs.google.com/spreadsheets/d/ " + googleFileId);
                } else if (inputUrl.contains("/presentation/")) {
                    queries.add("site:docs.google.com/presentation/d/ " + googleFileId);
                } else if (inputUrl.contains("/forms/")) {
                    queries.add("site:docs.google.com/forms/ " + googleFileId);
                } else if (inputUrl.contains("/drawings/")) {
                    queries.add("site:docs.google.com/drawings/d/ " + googleFileId);
                } else {
                    queries.add("site:docs.google.com " + googleFileId);
                }
            } else if (host.contains("drive.google.com")) {
                queries.add("site:drive.google.com " + googleFileId);
            }
        }

        return new ArrayList<>(queries);
    }

    private GoogleSearchResult runGoogleSearch(String query) {
        HttpURLConnection connection = null;

        try {
            String googleUrl = "https://www.google.com/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&num=10&hl=en";

            connection = (HttpURLConnection) new URL(googleUrl).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getUserAgent());
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();

            String html = readResponseBody(connection, statusCode);
            String body = html == null ? "" : html.toLowerCase();

            if (statusCode == 429 || statusCode == 503
                    || body.contains("our systems have detected unusual traffic")
                    || body.contains("unusual traffic from your computer network")
                    || body.contains("/sorry/index")
                    || body.contains("detected unusual traffic")
                    || body.contains("to continue, please type the characters below")
                    || body.contains("about this page")) {
                return new GoogleSearchResult(
                        "GOOGLE_BLOCK",
                        new ArrayList<>(),
                        "Google blocked the automated search request."
                );
            }

            if (statusCode >= 400) {
                return new GoogleSearchResult(
                        "ERROR",
                        new ArrayList<>(),
                        "Google returned HTTP " + statusCode
                );
            }

            List<String> resultUrls = extractGoogleResultUrls(html);

            return new GoogleSearchResult(
                    "OK",
                    resultUrls,
                    "Google search completed."
            );

        } catch (Exception e) {
            return new GoogleSearchResult(
                    "ERROR",
                    new ArrayList<>(),
                    "Google search error: " + e.getMessage()
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean matchesGoogleResults(String inputUrl, String finalUrl, List<String> resultUrls) {
        if (resultUrls == null || resultUrls.isEmpty()) {
            return false;
        }

        String normalizedInput = normalizeUrlForCompare(inputUrl);
        String normalizedFinal = normalizeUrlForCompare(finalUrl);
        String inputFileId = extractGoogleFileId(inputUrl);
        boolean inputIsGoogleFile = isGoogleFileUrl(inputUrl);

        for (String resultUrl : resultUrls) {
            String normalizedResult = normalizeUrlForCompare(resultUrl);

            if (isSameUrlForIndexCheck(normalizedInput, normalizedResult)) {
                return true;
            }

            if (!normalizedFinal.isEmpty()
                    && isSameUrlForIndexCheck(normalizedFinal, normalizedResult)) {
                return true;
            }

            if (inputIsGoogleFile && inputFileId != null && !inputFileId.isEmpty()) {
                String resultFileId = extractGoogleFileId(resultUrl);

                if (inputFileId.equals(resultFileId)) {
                    return true;
                }

                if (resultUrl.contains(inputFileId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String buildIndexedNote(String inputUrl) {
        if (isGoogleFileUrl(inputUrl)) {
            return "URL was found in Google organic results by matching the Google file ID from the input URL.";
        }

        return "URL was found in Google organic results.";
    }

    private List<String> extractGoogleResultUrls(String html) {
        List<String> urls = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return urls;
        }

        String[] parts = html.split("href=\"");

        for (String part : parts) {
            int end = part.indexOf("\"");

            if (end <= 0) {
                continue;
            }

            String href = part.substring(0, end)
                    .replace("&amp;", "&")
                    .trim();

            String extracted = "";

            if (href.startsWith("/url?")) {
                extracted = getQueryParamFromRelativeUrl(href, "q");

                if (extracted == null || extracted.isEmpty()) {
                    extracted = getQueryParamFromRelativeUrl(href, "url");
                }
            } else if (href.startsWith("http://") || href.startsWith("https://")) {
                extracted = href;
            }

            if (!extracted.isEmpty()
                    && !isGoogleInternalUrl(extracted)) {
                urls.add(urlDecodeQuietly(extracted));
            }
        }

        return urls;
    }

    private String getQueryParamFromRelativeUrl(String href, String key) {
        try {
            int questionIndex = href.indexOf("?");

            if (questionIndex < 0 || questionIndex + 1 >= href.length()) {
                return "";
            }

            String query = href.substring(questionIndex + 1);
            String[] params = query.split("&");

            for (String param : params) {
                int equalIndex = param.indexOf("=");

                if (equalIndex <= 0) {
                    continue;
                }

                String paramKey = param.substring(0, equalIndex);
                String paramValue = param.substring(equalIndex + 1);

                if (key.equals(paramKey)) {
                    return urlDecodeQuietly(paramValue);
                }
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private boolean isGoogleInternalUrl(String url) {
        String lower = url.toLowerCase();

        return lower.contains("google.com/search")
                || lower.contains("google.com/preferences")
                || lower.contains("google.com/advanced_search")
                || lower.contains("webcache.googleusercontent.com")
                || lower.contains("accounts.google.com")
                || lower.contains("policies.google.com")
                || lower.contains("support.google.com")
                || lower.contains("maps.google.com")
                || lower.contains("translate.google.com");
    }

    private String extractGoogleFileId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            String decodedUrl = urlDecodeQuietly(url);

            String[] patterns = {
                    "/document/d/",
                    "/spreadsheets/d/",
                    "/presentation/d/",
                    "/drawings/d/",
                    "/file/d/",
                    "/folders/",
                    "/forms/d/",
                    "/forms/d/e/"
            };

            for (String pattern : patterns) {
                int start = decodedUrl.indexOf(pattern);

                if (start >= 0) {
                    start += pattern.length();

                    int endSlash = decodedUrl.indexOf("/", start);
                    int endQuestion = decodedUrl.indexOf("?", start);
                    int endHash = decodedUrl.indexOf("#", start);

                    int end = decodedUrl.length();

                    if (endSlash > start) {
                        end = Math.min(end, endSlash);
                    }

                    if (endQuestion > start) {
                        end = Math.min(end, endQuestion);
                    }

                    if (endHash > start) {
                        end = Math.min(end, endHash);
                    }

                    String fileId = decodedUrl.substring(start, end).trim();

                    if (!fileId.isEmpty()) {
                        return fileId;
                    }
                }
            }

            URI uri = URI.create(decodedUrl);
            String idParam = getQueryParam(uri.getRawQuery(), "id");

            if (idParam != null && !idParam.isEmpty()) {
                return idParam;
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private String getQueryParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }

        String[] params = rawQuery.split("&");

        for (String param : params) {
            int equalIndex = param.indexOf("=");

            if (equalIndex <= 0) {
                continue;
            }

            String paramKey = urlDecodeQuietly(param.substring(0, equalIndex));
            String paramValue = urlDecodeQuietly(param.substring(equalIndex + 1));

            if (key.equals(paramKey)) {
                return paramValue;
            }
        }

        return null;
    }

    private boolean isGoogleFileUrl(String url) {
        String host = getHostQuietly(url);

        return host.contains("docs.google.com")
                || host.contains("drive.google.com");
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

    private String urlDecodeQuietly(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private boolean isSameUrlForIndexCheck(String target, String result) {
        if (target == null || result == null || target.isEmpty() || result.isEmpty()) {
            return false;
        }

        String targetNoSlash = target.replaceAll("/$", "");
        String resultNoSlash = result.replaceAll("/$", "");

        return resultNoSlash.equals(targetNoSlash);
    }

    /*
     * Audit helpers
     */
    private UrlCheckResult checkUrl(String url) {
        UrlCheckResult result = new UrlCheckResult();
        result.originalUrl = url;
        result.finalUrl = url;
        result.httpStatus = "UNKNOWN";

        if (!isValidHttpUrl(url)) {
            result.alive = false;
            result.error = "URL không hợp lệ.";
            return result;
        }

        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getUserAgent());
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();
            String finalUrl = connection.getURL().toString();

            String html = readResponseBody(connection, statusCode);
            String xRobotsTag = connection.getHeaderField("X-Robots-Tag");

            result.httpStatus = String.valueOf(statusCode);
            result.finalUrl = finalUrl;
            result.alive = statusCode >= 200 && statusCode < 400;
            result.title = extractTitle(html);
            result.noindex = hasNoindex(html, xRobotsTag);
            result.blocked = isBlocked(statusCode, html);
            result.loginWall = isLoginWall(html);
            result.error = "";

        } catch (Exception e) {
            result.httpStatus = "ERROR";
            result.alive = false;
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
            InputStream stream;

            if (statusCode >= 200 && statusCode < 400) {
                stream = connection.getInputStream();
            } else {
                stream = connection.getErrorStream();
            }

            if (stream == null) {
                return "";
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

            StringBuilder html = new StringBuilder();
            String line;
            int maxChars = 300000;

            while ((line = reader.readLine()) != null && html.length() < maxChars) {
                html.append(line).append("\n");
            }

            reader.close();
            return html.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasNoindex(String html, String xRobotsTag) {
        if (xRobotsTag != null && xRobotsTag.toLowerCase().contains("noindex")) {
            return true;
        }

        if (html == null || html.isEmpty()) {
            return false;
        }

        String lower = html.toLowerCase();

        return lower.contains("name=\"robots\"")
                && lower.contains("noindex");
    }

    private boolean isBlocked(int statusCode, String html) {
        if (statusCode == 403 || statusCode == 429 || statusCode == 503) {
            return true;
        }

        if (html == null || html.isEmpty()) {
            return false;
        }

        String lower = html.toLowerCase();

        return lower.contains("captcha")
                || lower.contains("access denied")
                || lower.contains("forbidden")
                || lower.contains("cloudflare")
                || lower.contains("checking your browser")
                || lower.contains("verify you are human");
    }

    private boolean isLoginWall(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        String lower = html.toLowerCase();

        boolean hasLoginText =
                lower.contains("login")
                        || lower.contains("sign in")
                        || lower.contains("log in")
                        || lower.contains("đăng nhập")
                        || lower.contains("please sign in")
                        || lower.contains("please log in");

        return hasLoginText && html.length() < 80000;
    }

    private String extractTitle(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String lower = html.toLowerCase();
        int start = lower.indexOf("<title>");
        int end = lower.indexOf("</title>");

        if (start >= 0 && end > start) {
            return html.substring(start + 7, end)
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        return "";
    }

    /*
     * Common helpers
     */
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

    private String cleanUrl(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }

        return rawUrl.trim();
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
                .replaceAll("/edit$", "")
                .replaceAll("/view$", "")
                .replaceAll("/preview$", "")
                .replaceAll("/pub$", "")
                .replaceAll("/viewform$", "")
                .replaceAll("/$", "")
                .trim();
    }

    private String getUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Không rõ lỗi.";
        }

        if (message.length() > 160) {
            return message.substring(0, 160) + "...";
        }

        return message;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeXml(String value) {
        return escapeHtml(value);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignored) {
        }
    }

    /*
     * Result classes
     */
    private static class FastPingResult {
        boolean success;
        String httpStatus;
        String error;

        FastPingResult(boolean success, String httpStatus, String error) {
            this.success = success;
            this.httpStatus = httpStatus;
            this.error = error;
        }
    }

    private static class GoogleIndexResult {
        Boolean indexed;
        String status;
        String note;

        GoogleIndexResult(Boolean indexed, String status, String note) {
            this.indexed = indexed;
            this.status = status;
            this.note = note;
        }
    }

    private static class GoogleSearchResult {
        String status;
        List<String> resultUrls;
        String note;

        GoogleSearchResult(String status, List<String> resultUrls, String note) {
            this.status = status;
            this.resultUrls = resultUrls;
            this.note = note;
        }
    }

    private static class UrlCheckResult {
        String originalUrl;
        String finalUrl;
        String httpStatus;
        String title;
        boolean alive;
        boolean noindex;
        boolean blocked;
        boolean loginWall;
        String error;

        boolean canSubmit() {
            return alive && !noindex && !blocked && !loginWall;
        }

        String getSkipReason() {
            if (error != null && !error.isEmpty()) {
                return error;
            }

            if (!alive) {
                return "URL chết hoặc không phản hồi HTTP 2xx/3xx.";
            }

            if (noindex) {
                return "URL có noindex.";
            }

            if (blocked) {
                return "URL bị chặn, captcha, Cloudflare hoặc server từ chối.";
            }

            if (loginWall) {
                return "URL có dấu hiệu login wall, bot có thể không đọc được.";
            }

            return "URL không đủ điều kiện crawl rõ ràng.";
        }
    }
}