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
     * Chỉ nên dùng Google Indexing API cho domain m sở hữu/đã xác minh.
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
     * Flow mới:
     * Check URL trước -> URL OK mới publish.
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
     * Check link sống/chết, final URL, title, noindex, blocked.
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
     * Direct Ping / Discovery Submit
     *
     * Flow mới:
     * 1. Check URL sống/chết.
     * 2. Check noindex / blocked / login wall.
     * 3. Nếu OK thì đánh dấu submitted_for_discovery.
     *
     * Lưu ý:
     * Đây KHÔNG phải Google Indexing API.
     * Dùng cho link social, Medium, GitHub, profile, bài share...
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

            UrlCheckResult checked = checkUrl(url);

            item.put("httpStatus", checked.httpStatus);
            item.put("finalUrl", checked.finalUrl);
            item.put("alive", checked.alive);
            item.put("title", checked.title);
            item.put("noindex", checked.noindex);
            item.put("blocked", checked.blocked);
            item.put("loginWall", checked.loginWall);

            if (checked.canSubmit()) {
                item.put("pingStatus", "submitted_for_discovery");
                item.put("message", "URL OK. Đã gửi direct request/discovery signal. Không đảm bảo Google index.");
            } else {
                item.put("pingStatus", "skipped");
                item.put("message", checked.getSkipReason());
            }

            result.add(item);

            sleepQuietly(300);
        }

        return result;
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

        /*
         * Nếu HTML quá ngắn và có dấu hiệu login thì khả năng cao là login wall.
         */
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