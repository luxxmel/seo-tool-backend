package com.seo.seotool.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private static final int DISCOVERY_PAGE_SIZE = 30;

    @Value("${indexflow.public-base-url:https://indextool.netlify.app}")
    private String publicBaseUrl;

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

            boolean eligible = ping.success && ping.statusCode >= 200 && ping.statusCode < 400;
            String pingStatus = eligible ? "queued" : "rejected";
            String discoveryStatus = eligible ? "DISCOVERY_QUEUED" : "URL_UNAVAILABLE";

            System.out.println("========== SAVE SUPABASE ==========");
            System.out.println("SAVE URL: " + url);
            System.out.println("SAVE HTTP STATUS: " + ping.httpStatus);
            System.out.println("SAVE PING STATUS: " + pingStatus);
            System.out.println("SAVE DISCOVERY STATUS: " + discoveryStatus);

            if (eligible) {
                supabaseService.saveOrUpdateUrl(
                        url,
                        ping.httpStatus,
                        pingStatus,
                        discoveryStatus
                );
            }

            System.out.println("========== SAVE DONE ==========");

            item.put("httpStatus", ping.httpStatus);
            item.put("pingStatus", pingStatus);
            item.put("discoveryStatus", discoveryStatus);
            item.put("method", "DISCOVERY_BATCH");
            item.put("addedToHub", eligible);
            item.put("message", eligible
                    ? "URL hoạt động và đã được xếp vào Discovery Hub."
                    : "URL không đủ điều kiện đưa vào Discovery Hub: " + buildFastPingMessage(ping));

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

            return new FastPingResult(true, statusCode, String.valueOf(statusCode), "");

        } catch (Exception e) {
            return new FastPingResult(false, 0, "ERROR", e.getMessage());

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
        return renderDiscoveryLandingHtml();
    }

    public String renderDiscoveryLandingHtml() {
        List<String> urls = getIndexHubUrls();
        int pageCount = Math.max(1, (int) Math.ceil(urls.size() / (double) DISCOVERY_PAGE_SIZE));
        String canonical = publicUrl("/discovery");

        StringBuilder html = htmlHead(
                "MECI Discovery Hub - IndexFlow",
                "Danh mục trang discovery công khai giúp Google phát hiện các URL MECI mới nhất.",
                canonical
        );
        html.append("<body><main class=\"wrap\">");
        html.append("<p class=\"eyebrow\">INDEXFLOW DISCOVERY</p>");
        html.append("<h1>MECI Discovery Hub</h1>");
        html.append("<p class=\"lead\">Các URL được chia thành từng lô nhỏ để bot dễ thu thập hơn. Trang này chỉ hỗ trợ discovery, không bảo đảm URL ngoài sẽ được Google lập chỉ mục.</p>");
        html.append("<div class=\"stats\"><strong>").append(urls.size()).append("</strong> URL · <strong>").append(pageCount).append("</strong> trang</div>");
        html.append("<section class=\"grid\">");
        for (int page = 1; page <= pageCount; page++) {
            int from = (page - 1) * DISCOVERY_PAGE_SIZE;
            int to = Math.min(from + DISCOVERY_PAGE_SIZE, urls.size());
            int count = Math.max(0, to - from);
            html.append("<article class=\"card\"><h2>Lô discovery ").append(page).append("</h2>")
                    .append("<p>").append(count).append(" URL công khai trong lô này.</p>")
                    .append("<a class=\"button\" href=\"").append(publicUrl("/discovery/page/" + page)).append("\">Xem lô ").append(page).append("</a></article>");
        }
        html.append("</section><p class=\"tools\"><a href=\"").append(publicUrl("/discovery-sitemap.xml")).append("\">Discovery sitemap</a> · <a href=\"").append(publicUrl("/discovery-rss.xml")).append("\">RSS feed</a></p>");
        html.append("</main></body></html>");
        return html.toString();
    }

    public String renderDiscoveryPageHtml(int page) {
        List<String> urls = getIndexHubUrls();
        int pageCount = Math.max(1, (int) Math.ceil(urls.size() / (double) DISCOVERY_PAGE_SIZE));
        int safePage = Math.max(1, Math.min(page, pageCount));
        int from = Math.min((safePage - 1) * DISCOVERY_PAGE_SIZE, urls.size());
        int to = Math.min(from + DISCOVERY_PAGE_SIZE, urls.size());
        List<String> batch = urls.subList(from, to);
        String canonical = publicUrl("/discovery/page/" + safePage);

        StringBuilder html = htmlHead(
                "Discovery Batch " + safePage + " - MECI IndexFlow",
                "Lô URL MECI công khai số " + safePage + " phục vụ quá trình khám phá liên kết.",
                canonical
        );
        html.append("<body><main class=\"wrap\">");
        html.append("<p class=\"eyebrow\"><a href=\"").append(publicUrl("/discovery")).append("\">DISCOVERY HUB</a></p>");
        html.append("<h1>Lô discovery ").append(safePage).append("</h1>");
        html.append("<p class=\"lead\">Mỗi URL bên dưới là một liên kết HTML công khai. Google có thể lần theo liên kết, nhưng quyền quyết định crawl và index vẫn thuộc về Google.</p>");
        html.append("<div class=\"stats\"><strong>").append(batch.size()).append("</strong> URL trong lô</div>");

        if (batch.isEmpty()) {
            html.append("<article class=\"card\"><p>Chưa có URL trong lô này.</p></article>");
        } else {
            html.append("<section class=\"list\">");
            int number = from + 1;
            for (String url : batch) {
                String host = hostOf(url);
                html.append("<article class=\"card\"><span class=\"number\">").append(number++).append("</span>")
                        .append("<div><h2>").append(escapeHtml(host.isBlank() ? "Liên kết MECI" : host)).append("</h2>")
                        .append("<p>Trang hồ sơ, bài viết hoặc tài nguyên công khai liên quan đến hệ thống MECI Việt Nam.</p>")
                        .append("<a href=\"").append(escapeHtml(url)).append("\" rel=\"noopener\">Xem nguồn: ").append(escapeHtml(url)).append("</a></div></article>");
            }
            html.append("</section>");
        }

        html.append("<nav class=\"pager\">");
        if (safePage > 1) html.append("<a href=\"").append(publicUrl("/discovery/page/" + (safePage - 1))).append("\">← Trang trước</a>");
        html.append("<span>Trang ").append(safePage).append("/").append(pageCount).append("</span>");
        if (safePage < pageCount) html.append("<a href=\"").append(publicUrl("/discovery/page/" + (safePage + 1))).append("\">Trang sau →</a>");
        html.append("</nav></main></body></html>");
        return html.toString();
    }

    public String renderIndexHubSitemapXml() {
        return renderDiscoverySitemapXml();
    }

    public String renderDiscoverySitemapXml() {
        List<String> urls = getIndexHubUrls();
        int pageCount = Math.max(1, (int) Math.ceil(urls.size() / (double) DISCOVERY_PAGE_SIZE));
        String today = java.time.LocalDate.now().toString();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendSitemapUrl(xml, publicUrl("/discovery"), today);
        for (int page = 1; page <= pageCount; page++) {
            appendSitemapUrl(xml, publicUrl("/discovery/page/" + page), today);
        }
        xml.append("</urlset>");
        return xml.toString();
    }

    public String renderIndexHubRssXml() {
        return renderDiscoveryRssXml();
    }

    public String renderDiscoveryRssXml() {
        List<String> urls = getIndexHubUrls();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\"><channel>\n");
        xml.append("<title>MECI Discovery Feed</title><link>").append(escapeXml(publicUrl("/discovery"))).append("</link>");
        xml.append("<description>Latest public MECI URLs queued for discovery.</description>\n");
        for (String url : urls.stream().limit(50).toList()) {
            xml.append("<item><title>").append(escapeXml(hostOf(url))).append("</title><link>").append(escapeXml(url)).append("</link><guid>").append(escapeXml(url)).append("</guid></item>\n");
        }
        xml.append("</channel></rss>");
        return xml.toString();
    }

    public Map<String, Object> getDiscoverySummary() {
        List<String> urls = getIndexHubUrls();
        int pages = Math.max(1, (int) Math.ceil(urls.size() / (double) DISCOVERY_PAGE_SIZE));
        Map<String, Object> data = new HashMap<>();
        data.put("totalUrls", urls.size());
        data.put("pageSize", DISCOVERY_PAGE_SIZE);
        data.put("pageCount", pages);
        data.put("publicUrl", publicUrl("/discovery"));
        data.put("sitemapUrl", publicUrl("/discovery-sitemap.xml"));
        data.put("rssUrl", publicUrl("/discovery-rss.xml"));
        return data;
    }

    private StringBuilder htmlHead(String title, String description, String canonical) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta name=\"robots\" content=\"index,follow\">")
                .append("<title>").append(escapeHtml(title)).append("</title>")
                .append("<meta name=\"description\" content=\"").append(escapeHtml(description)).append("\">")
                .append("<link rel=\"canonical\" href=\"").append(escapeHtml(canonical)).append("\">")
                .append("<style>")
                .append("*{box-sizing:border-box}body{margin:0;font-family:Inter,Arial,sans-serif;background:#07111f;color:#e5eef8;line-height:1.65}.wrap{max-width:1080px;margin:auto;padding:54px 20px 80px}.eyebrow{font-size:12px;letter-spacing:.18em;color:#67e8f9;font-weight:800}.eyebrow a{color:inherit}.lead{max-width:820px;color:#a9bad0}.stats{display:inline-block;margin:12px 0 28px;padding:8px 14px;border:1px solid #24405f;border-radius:999px;background:#0d1c30;color:#c7d8eb}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:16px}.list{display:grid;gap:14px}.card{display:flex;gap:14px;padding:20px;border:1px solid #203a57;border-radius:18px;background:linear-gradient(145deg,#0b1a2d,#0e2239);box-shadow:0 16px 35px rgba(0,0,0,.2)}.card h2{font-size:17px;margin:0 0 6px}.card p{margin:0 0 10px;color:#9fb2c9}.card a{color:#67e8f9;word-break:break-all}.button{display:inline-block;padding:9px 13px;border-radius:10px;background:#0e7490;color:white!important;text-decoration:none}.number{width:34px;height:34px;display:grid;place-items:center;flex:0 0 34px;border-radius:50%;background:#123454;color:#67e8f9;font-weight:800}.pager{display:flex;justify-content:space-between;gap:12px;margin-top:28px;padding-top:18px;border-top:1px solid #203a57}.pager a,.tools a{color:#67e8f9}.tools{margin-top:28px;color:#9fb2c9}@media(max-width:600px){.pager{flex-wrap:wrap}.wrap{padding-top:34px}}")
                .append("</style></head>");
        return html;
    }

    private void appendSitemapUrl(StringBuilder xml, String url, String lastmod) {
        xml.append("<url><loc>").append(escapeXml(url)).append("</loc><lastmod>").append(lastmod).append("</lastmod></url>\n");
    }

    private String publicUrl(String path) {
        String base = publicBaseUrl == null ? "https://indextool.netlify.app" : publicBaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private String hostOf(String rawUrl) {
        try {
            String host = URI.create(rawUrl).getHost();
            return host == null ? "" : host;
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
        final boolean success;
        final int statusCode;
        final String httpStatus;
        final String error;

        FastPingResult(boolean success, int statusCode, String httpStatus, String error) {
            this.success = success;
            this.statusCode = statusCode;
            this.httpStatus = httpStatus;
            this.error = error;
        }
    }
}