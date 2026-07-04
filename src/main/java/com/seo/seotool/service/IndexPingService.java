package com.seo.seotool.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IndexPingService {

    @Autowired
    private SupabaseService supabaseService;

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

    public List<Map<String, Object>> processDirectPing(List<String> urls) {
        List<Map<String, Object>> result = new ArrayList<>();

        System.out.println("========== DIRECT PING START ==========");

        if (urls == null || urls.isEmpty()) {
            System.out.println("DIRECT PING: URL list empty");
            return result;
        }

        Set<String> cleanUrls = new LinkedHashSet<>();

        for (String rawUrl : urls) {
            String url = cleanUrl(rawUrl);

            if (!url.isEmpty()) {
                cleanUrls.add(url);
            }
        }

        System.out.println("DIRECT PING URL COUNT: " + cleanUrls.size());

        for (String url : cleanUrls) {
            Map<String, Object> item = new HashMap<>();
            item.put("url", url);
            item.put("time", LocalTime.now().toString().substring(0, 8));

            System.out.println("DIRECT PING URL: " + url);

            if (!isValidHttpUrl(url)) {
                item.put("httpStatus", "INVALID");
                item.put("pingStatus", "failed");
                item.put("discoveryStatus", "INVALID_URL");
                item.put("method", "SKIPPED");
                item.put("addedToHub", false);
                item.put("message", "URL sai định dạng. Chỉ nhận http:// hoặc https://.");

                System.out.println("DIRECT PING INVALID URL: " + url);

                result.add(item);
                continue;
            }

            FastPingResult ping = fastPingUrl(url);

            String pingStatus = ping.success ? "sent" : "attempted";
            String discoveryStatus = ping.success ? "PING_SENT" : "PING_ATTEMPTED";

            System.out.println("========== SAVE SUPABASE ==========");
            System.out.println("SAVE URL: " + url);
            System.out.println("SAVE HTTP STATUS: " + ping.httpStatus);
            System.out.println("SAVE PING STATUS: " + pingStatus);
            System.out.println("SAVE DISCOVERY STATUS: " + discoveryStatus);

            supabaseService.saveOrUpdateUrl(
                    url,
                    ping.httpStatus,
                    pingStatus,
                    discoveryStatus
            );

            System.out.println("========== SAVE DONE ==========");

            item.put("httpStatus", ping.httpStatus);
            item.put("pingStatus", pingStatus);
            item.put("discoveryStatus", discoveryStatus);
            item.put("method", "FAST_DIRECT_SUPABASE");
            item.put("addedToHub", true);
            item.put("message", buildFastPingMessage(ping) + " URL đã được gửi sang Supabase Index Hub.");

            result.add(item);

            sleepQuietly(80);
        }

        System.out.println("========== DIRECT PING END ==========");

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

            return new FastPingResult(true, String.valueOf(statusCode), "");

        } catch (Exception e) {
            return new FastPingResult(false, "ERROR", e.getMessage());

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

    public List<String> getIndexHubUrls() {
        return supabaseService.getLatestUrls();
    }

    public String renderIndexHubHtml() {
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

    public String renderIndexHubSitemapXml() {
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

    public String renderIndexHubRssXml() {
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
}