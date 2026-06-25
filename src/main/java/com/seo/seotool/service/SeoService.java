package com.seo.seotool.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.indexing.v3.Indexing;
import com.google.api.services.indexing.v3.model.UrlNotification;
import org.springframework.core.io.ClassPathResource;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeoService {

    private static final List<String> SCOPES =
            Collections.singletonList("https://www.googleapis.com/auth/indexing");

    private static final String APPLICATION_NAME = "seotool";

    /*
     * Chỉ nên dùng Google Indexing API cho domain m sở hữu hoặc đã xác minh.
     * Sửa danh sách này theo domain của m.
     */
    private static final List<String> ALLOWED_INDEXING_DOMAINS =
            Arrays.asList(
                    "mecivietnam.com",
                    "www.mecivietnam.com"
            );

    /*
     * Google Indexing API
     * Dùng cho URL thuộc domain đã xác minh trong Google Search Console.
     */
    public String processPing(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "Không có URL!";
        }

        int success = 0;
        int fail = 0;
        int skipped = 0;
        StringBuilder log = new StringBuilder();

        try {
            InputStream is = new ClassPathResource("service_account.json").getInputStream();

            GoogleCredential credential = GoogleCredential
                    .fromStream(is)
                    .createScoped(SCOPES);

            Indexing service = new Indexing.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
            )
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            for (String rawUrl : urls) {
                String url = cleanUrl(rawUrl);

                if (url.isEmpty()) {
                    continue;
                }

                try {
                    if (!isAllowedIndexingDomain(url)) {
                        skipped++;
                        log.append("SKIP ")
                                .append(url)
                                .append(" | URL không thuộc domain đã khai báo cho Google Indexing API.\n");
                        continue;
                    }

                    UrlCheckResult checked = checkUrl(url);

                    if (!checked.canSubmit()) {
                        skipped++;
                        log.append("SKIP ")
                                .append(url)
                                .append(" | ")
                                .append(checked.getSkipReason())
                                .append("\n");
                        continue;
                    }

                    UrlNotification notification = new UrlNotification()
                            .setUrl(checked.finalUrl)
                            .setType("URL_UPDATED");

                    service.urlNotifications()
                            .publish(notification)
                            .execute();

                    success++;
                    log.append("OK ")
                            .append(checked.finalUrl)
                            .append(" | Google Indexing API submitted\n");

                    Thread.sleep(500);
                } catch (Exception e) {
                    fail++;

                    String message = getFriendlyGoogleError(e.getMessage());

                    log.append("FAIL ")
                            .append(url)
                            .append(" | ")
                            .append(message)
                            .append("\n");
                }
            }

        } catch (Exception e) {
            return "Lỗi khởi tạo Google Indexing API: " + e.getMessage();
        }

        return String.format(
                "Thành công: %d | Bỏ qua: %d | Thất bại: %d%n%s",
                success,
                skipped,
                fail,
                log
        );
    }

    /*
     * Audit URL
     * Check link sống, chết, final URL, title, noindex, blocked.
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
            item.put("note", checked.canSubmit() ? "URL OK, có thể submit/discovery." : checked.getSkipReason());

            result.add(item);
        }

        return result;
    }

    /*
     * Check Index bằng chính URL user nhập.
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
                if (googleIndex.note != null && !googleIndex.note.isEmpty()) {
                    note = googleIndex.note;
                } else {
                    note = "URL was found in Google organic results.";
                }

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
     * Direct Ping / Force Discovery Submit
     *
     * Logic mới:
     * - URL hợp lệ về cú pháp là ping hết.
     * - Không chặn vì 403, 404, 429, 500, timeout, noindex, login wall, Cloudflare.
     * - Ping trước, check sau.
     * - Check chỉ dùng để báo cáo trạng thái, không quyết định có ping hay không.
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
                item.put("finalUrl", url);
                item.put("alive", false);
                item.put("title", "");
                item.put("noindex", false);
                item.put("blocked", false);
                item.put("loginWall", false);
                item.put("pingStatus", "failed");
                item.put("discoveryStatus", "INVALID_URL");
                item.put("message", "URL sai định dạng nên không thể ping.");

                result.add(item);
                sleepQuietly(150);
                continue;
            }

            ForcePingResult pingResult = forcePingUrl(url);

            item.put("pingStatus", "submitted_for_discovery");
            item.put("discoveryStatus", pingResult.success ? "PING_SENT" : "PING_ATTEMPTED");
            item.put("pingHttpStatus", pingResult.httpStatus);
            item.put("pingError", pingResult.error);

            /*
             * Sau khi ping xong mới check.
             * Check lỗi cũng không làm URL bị fail.
             */
            UrlCheckResult checked = checkUrl(url);

            item.put("httpStatus", checked.httpStatus);
            item.put("finalUrl", checked.finalUrl);
            item.put("alive", checked.alive);
            item.put("title", checked.title);
            item.put("noindex", checked.noindex);
            item.put("blocked", checked.blocked);
            item.put("loginWall", checked.loginWall);

            item.put("message", buildForcePingMessage(pingResult, checked));

            result.add(item);

            sleepQuietly(250);
        }

        return result;
    }

    private ForcePingResult forcePingUrl(String url) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getUserAgent());
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();

            try {
                InputStream stream = statusCode >= 200 && statusCode < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                if (stream != null) {
                    byte[] buffer = new byte[1024];
                    stream.read(buffer);
                    stream.close();
                }
            } catch (Exception ignored) {
            }

            return new ForcePingResult(
                    true,
                    String.valueOf(statusCode),
                    ""
            );

        } catch (Exception e) {
            return new ForcePingResult(
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

    private String buildForcePingMessage(ForcePingResult pingResult, UrlCheckResult checked) {
        String baseMessage;

        if (pingResult.success) {
            baseMessage = "Đã gửi force ping tới URL.";
        } else {
            baseMessage = "Đã thử force ping nhưng backend bị lỗi kết nối tới URL.";
        }

        if ("ERROR".equals(checked.httpStatus)) {
            return baseMessage + " Backend không đọc được trạng thái URL, nhưng request ping đã được xử lý.";
        }

        if (checked.blocked) {
            return baseMessage + " URL có dấu hiệu bị chặn server, Cloudflare, captcha hoặc 429/403.";
        }

        if (checked.noindex) {
            return baseMessage + " URL có noindex, vẫn đã ping theo chế độ force.";
        }

        if (checked.loginWall) {
            return baseMessage + " URL có dấu hiệu login wall, vẫn đã ping theo chế độ force.";
        }

        if (!checked.alive) {
            return baseMessage + " URL không trả HTTP 2xx/3xx, vẫn đã ping theo chế độ force.";
        }

        return baseMessage + " URL hiện truy cập được.";
    }

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
     * Check URL cơ bản:
     * - HTTP status
     * - final URL
     * - title
     * - meta robots noindex
     * - X-Robots-Tag noindex
     * - blocked/captcha/login wall
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

    private boolean isAllowedIndexingDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            if (host == null) {
                return false;
            }

            host = host.toLowerCase();

            for (String domain : ALLOWED_INDEXING_DOMAINS) {
                if (host.equals(domain.toLowerCase())) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
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

    private String getFriendlyGoogleError(String message) {
        if (message == null) {
            return "Không rõ lỗi.";
        }

        if (message.contains("403")) {
            return "403 Forbidden - Service account chưa có quyền owner trong Search Console hoặc URL không thuộc domain đã xác minh.";
        }

        if (message.contains("429")) {
            return "429 Too Many Requests - Vượt quota hoặc gửi quá nhiều request.";
        }

        if (message.contains("400")) {
            return "400 Bad Request - URL không hợp lệ hoặc request sai định dạng.";
        }

        return message;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignored) {
        }
    }

    private static class ForcePingResult {
        boolean success;
        String httpStatus;
        String error;

        ForcePingResult(boolean success, String httpStatus, String error) {
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
                return "URL có noindex, không nên submit.";
            }

            if (blocked) {
                return "URL bị chặn, captcha, Cloudflare hoặc server từ chối.";
            }

            if (loginWall) {
                return "URL có dấu hiệu login wall, bot có thể không đọc được.";
            }

            return "URL không đủ điều kiện submit.";
        }
    }
}