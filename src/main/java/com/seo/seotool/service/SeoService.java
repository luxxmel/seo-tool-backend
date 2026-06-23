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
import java.util.List;
import java.util.Map;

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
     *
     * Flow:
     * Check URL trước, URL OK mới publish.
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
     * Check Index thật bằng Google site:url.
     *
     * Logic:
     * - Vẫn check HTTP/noindex/blocked để hiển thị thông tin kỹ thuật.
     * - Vẫn kiểm tra Google bằng site:url kể cả URL bị 403, blocked hoặc server không đọc được.
     * - Không check toàn bộ HTML vì Google có thể trả lại chính query trong form search.
     * - Chỉ xác nhận INDEXED khi URL xuất hiện trong link kết quả Google dạng /url?q=...
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
            GoogleIndexResult googleIndex = checkGoogleIndexedBySiteSearch(url);

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
                if (checked.noindex) {
                    note = "Found in Google, but the URL currently has noindex.";
                } else if (checked.blocked) {
                    note = "Found in Google, but the server currently blocks automated access.";
                } else if (!checked.alive) {
                    note = "Found in Google, but the URL does not currently return HTTP 2xx/3xx.";
                } else {
                    note = "URL was found in Google organic results.";
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
     * Direct Ping / Discovery Submit
     *
     * Logic:
     * Dù URL bị chặn, 403, 429, noindex, login wall hoặc backend không đọc được
     * thì vẫn ghi nhận là đã gửi discovery signal.
     *
     * Chỉ fail nếu URL rỗng hoặc URL sai định dạng.
     *
     * Lưu ý:
     * Đây không phải Google Indexing API chính thức.
     * Dùng cho link social, Medium, GitHub, profile, bài share, tầng 2.
     * Không đảm bảo Google index.
     */
    public List<Map<String, Object>> processDirectPing(List<String> urls) {
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
                item.put("message", "URL không hợp lệ, không thể gửi discovery signal.");

                result.add(item);
                sleepQuietly(300);
                continue;
            }

            UrlCheckResult checked = checkUrl(url);

            item.put("httpStatus", checked.httpStatus);
            item.put("finalUrl", checked.finalUrl);
            item.put("alive", checked.alive);
            item.put("title", checked.title);
            item.put("noindex", checked.noindex);
            item.put("blocked", checked.blocked);
            item.put("loginWall", checked.loginWall);

            item.put("pingStatus", "submitted_for_discovery");

            if ("ERROR".equals(checked.httpStatus)) {
                item.put("message", "Backend không đọc được URL, nhưng vẫn đã gửi discovery signal.");
            } else if (checked.blocked) {
                item.put("message", "URL có dấu hiệu bị chặn server, nhưng vẫn đã gửi discovery signal.");
            } else if (checked.noindex) {
                item.put("message", "URL có noindex, nhưng vẫn đã gửi discovery signal theo yêu cầu.");
            } else if (checked.loginWall) {
                item.put("message", "URL có dấu hiệu login wall, nhưng vẫn đã gửi discovery signal.");
            } else if (!checked.alive) {
                item.put("message", "URL không trả HTTP 2xx/3xx, nhưng vẫn đã gửi discovery signal.");
            } else {
                item.put("message", "URL OK. Đã gửi discovery signal.");
            }

            result.add(item);

            sleepQuietly(300);
        }

        return result;
    }

    /*
     * Check Google Search bằng query site:url.
     * Chỉ lấy URL từ các link kết quả thật, tránh false positive từ query trong HTML.
     */
    private GoogleIndexResult checkGoogleIndexedBySiteSearch(String url) {
        HttpURLConnection connection = null;

        try {
            String query = "site:" + url;
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
                    || body.contains("detected unusual traffic")) {
                return new GoogleIndexResult(
                        null,
                        "GOOGLE_BLOCK",
                        "Google blocked the automated search request."
                );
            }

            if (body.contains("did not match any documents")
                    || body.contains("no results found")
                    || body.contains("your search -")
                    || body.contains("không khớp với bất kỳ tài liệu nào")
                    || body.contains("không tìm thấy kết quả nào")) {
                return new GoogleIndexResult(
                        false,
                        "NOT_INDEXED",
                        "URL was not found in Google results."
                );
            }

            List<String> resultUrls = extractGoogleResultUrls(html);
            String target = normalizeUrlForCompare(url);

            for (String resultUrl : resultUrls) {
                String normalizedResult = normalizeUrlForCompare(resultUrl);

                if (isSameUrlForIndexCheck(target, normalizedResult)) {
                    return new GoogleIndexResult(
                            true,
                            "INDEXED",
                            "URL was found in Google organic results."
                    );
                }
            }

            return new GoogleIndexResult(
                    false,
                    "NOT_INDEXED",
                    "URL was not found in Google organic results."
            );

        } catch (Exception e) {
            return new GoogleIndexResult(
                    null,
                    "ERROR",
                    "Index check error: " + e.getMessage()
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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

            if (href.startsWith("/url?q=")) {
                extracted = href.substring(7);

                int ampIndex = extracted.indexOf("&");

                if (ampIndex > 0) {
                    extracted = extracted.substring(0, ampIndex);
                }
            } else if (href.startsWith("http://") || href.startsWith("https://")) {
                extracted = href;
            }

            if (!extracted.isEmpty()
                    && !extracted.contains("google.com")
                    && !extracted.contains("webcache.googleusercontent.com")
                    && !extracted.contains("accounts.google.com")
                    && !extracted.contains("policies.google.com")
                    && !extracted.contains("support.google.com")) {
                urls.add(urlDecodeQuietly(extracted));
            }
        }

        return urls;
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