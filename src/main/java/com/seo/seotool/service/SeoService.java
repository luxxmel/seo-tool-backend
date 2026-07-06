package com.seo.seotool.service;

import jakarta.servlet.http.HttpServletRequest;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeoService {

    private final IndexPingService indexPingService;
    private final SupabaseService supabaseService;

    public SeoService(IndexPingService indexPingService, SupabaseService supabaseService) {
        this.indexPingService = indexPingService;
        this.supabaseService = supabaseService;
    }

    /*
     * Ping Index / Index Hub đã tách sang IndexPingService.
     * Giữ các method này để Controller cũ vẫn gọi SeoService như trước, không bị vỡ route.
     */
    public String processPing(List<String> urls) {
        return indexPingService.processPing(urls);
    }

    public List<Map<String, Object>> processDirectPing(List<String> urls) {
        return indexPingService.processDirectPing(urls);
    }

    /*
     * Audit URL
     * Dùng khi muốn kiểm tra chi tiết: sống/chết/noindex/blocked/login wall.
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
            item.put("liveStatus", buildLiveStatus(checked));
            item.put("note", checked.canSubmit() ? "URL OK, có thể crawl/discovery." : checked.getSkipReason());

            result.add(item);
        }

        return result;
    }

    /*
     * Check Index + Check link còn sống
     *
     * Frontend dùng 3 nhóm dữ liệu:
     * - Check Index: indexStatus
     * - Link còn sống: alive/httpStatus/blocked/loginWall
     * - Trạng thái: note/liveStatus/statusLabel
     *
     * Bản sửa:
     * - Search Google bằng site/link như cũ.
     * - Parse URL trong href như cũ.
     * - Đọc thêm HTML Google Search để bắt fileId/path/title giống kiểu soi F12.
     */
    public List<Map<String, Object>> processCheckIndex(List<String> urls) {
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
                item.put("indexLabel", "Lỗi");
                item.put("liveStatus", "INVALID_URL");
                item.put("liveLabel", "Sai URL");
                item.put("statusLabel", "URL sai định dạng.");
                item.put("note", "URL sai định dạng. Chỉ nhận http:// hoặc https://.");

                result.add(item);
                sleepQuietly(250);
                continue;
            }

            UrlCheckResult checked = checkUrl(url);
            GoogleIndexResult googleIndex = checkGoogleIndexedSmart(url, checked);

            String liveStatus = buildLiveStatus(checked);
            String liveLabel = buildLiveLabel(liveStatus);
            String indexLabel = buildIndexLabel(googleIndex.status);
            String statusLabel = buildCombinedStatusLabel(checked, googleIndex, liveStatus);

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
            item.put("indexLabel", indexLabel);

            item.put("liveStatus", liveStatus);
            item.put("liveLabel", liveLabel);

            item.put("statusLabel", statusLabel);
            item.put("note", statusLabel);

            result.add(item);

            /*
             * Google có thể chặn nếu bắn quá nhanh.
             */
            sleepQuietly(900);
        }

        return result;
    }


    public Map<String, Object> addUrlsToHub(List<String> urls) {
        Map<String, Object> response = new HashMap<>();

        int added = 0;
        int skipped = 0;

        if (urls == null || urls.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không có URL nào được gửi lên.");
            response.put("added", 0);
            response.put("skipped", 0);
            return response;
        }

        Set<String> cleanUrls = new LinkedHashSet<>();

        for (String rawUrl : urls) {
            String url = cleanUrl(rawUrl);

            if (!url.isEmpty() && isValidHttpUrl(url)) {
                cleanUrls.add(url);
            } else {
                skipped++;
            }
        }

        for (String url : cleanUrls) {
            supabaseService.addUrlOnly(url);
            added++;
        }

        response.put("success", true);
        response.put("message", "Đã thêm URL vào Index Hub.");
        response.put("added", added);
        response.put("skipped", skipped);

        return response;
    }

    public Map<String, Object> pingDueUrls() {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();

        List<Map<String, Object>> dueTasks = supabaseService.getDueUrlTasks();

        int total = dueTasks.size();
        int successCount = 0;
        int failCount = 0;

        for (Map<String, Object> task : dueTasks) {
            long taskId = Long.parseLong(String.valueOf(task.get("id")));
            String url = String.valueOf(task.get("url"));

            Map<String, Object> item = new HashMap<>();
            item.put("id", taskId);
            item.put("url", url);

            try {
                List<String> singleUrl = new ArrayList<>();
                singleUrl.add(url);

                List<Map<String, Object>> pingResult = indexPingService.processDirectPing(singleUrl);

                boolean success = pingResult != null && !pingResult.isEmpty();
                String message = success ? "Auto ping completed." : "Auto ping returned empty result.";

                supabaseService.markPingResult(taskId, url, success, null, message);

                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }

                item.put("success", success);
                item.put("message", message);
                item.put("result", pingResult);
            } catch (Exception e) {
                failCount++;

                String message = safeMessage(e.getMessage());
                supabaseService.markPingResult(taskId, url, false, null, message);

                item.put("success", false);
                item.put("message", message);
            }

            details.add(item);
            sleepQuietly(500);
        }

        response.put("success", true);
        response.put("total", total);
        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("details", details);

        return response;
    }

    public List<Map<String, Object>> getUrlStatus() {
        return supabaseService.getUrlStatus();
    }

    public List<Map<String, Object>> getBotLogs() {
        return supabaseService.getBotLogs();
    }

    public void trackBotVisit(HttpServletRequest request) {
        if (request == null) {
            return;
        }

        try {
            String requestUrl = request.getRequestURL().toString();
            String queryString = request.getQueryString();

            if (queryString != null && !queryString.isBlank()) {
                requestUrl += "?" + queryString;
            }

            String userAgent = request.getHeader("User-Agent");
            String referer = request.getHeader("Referer");
            String ip = getClientIp(request);

            supabaseService.saveBotLog(requestUrl, userAgent, ip, referer);
        } catch (Exception e) {
            System.out.println("TRACK BOT VISIT ERROR: " + e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");

        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }

    private String buildIndexLabel(String indexStatus) {
        if ("INDEXED".equals(indexStatus)) {
            return "Đã Index";
        }

        if ("NOT_INDEXED".equals(indexStatus)) {
            return "Chưa thấy";
        }

        if ("VERIFY_NEEDED".equals(indexStatus)) {
            return "Cần kiểm tra";
        }

        if ("GOOGLE_BLOCK".equals(indexStatus)) {
            return "Google chặn";
        }

        if ("ERROR".equals(indexStatus)) {
            return "Lỗi";
        }

        return "Cần xem";
    }

    private String buildLiveStatus(UrlCheckResult checked) {
        if (checked == null) {
            return "UNKNOWN";
        }

        if ("INVALID".equals(checked.httpStatus)) {
            return "INVALID_URL";
        }

        if ("ERROR".equals(checked.httpStatus)) {
            return "UNKNOWN";
        }

        if (checked.blocked || checked.loginWall) {
            return "RESTRICTED";
        }

        if (checked.alive) {
            return "LIVE";
        }

        try {
            int code = Integer.parseInt(checked.httpStatus);

            if (code == 404 || code == 410) {
                return "DEAD";
            }

            if (code >= 300 && code < 400) {
                return "REDIRECT";
            }

            if (code >= 500) {
                return "SERVER_ERROR";
            }

            if (code == 403 || code == 429 || code == 503) {
                return "RESTRICTED";
            }
        } catch (Exception ignored) {
        }

        return "UNKNOWN";
    }

    private String buildLiveLabel(String liveStatus) {
        if ("LIVE".equals(liveStatus)) {
            return "Còn sống";
        }

        if ("REDIRECT".equals(liveStatus)) {
            return "Redirect";
        }

        if ("RESTRICTED".equals(liveStatus)) {
            return "Bị chặn";
        }

        if ("DEAD".equals(liveStatus)) {
            return "Có thể chết";
        }

        if ("SERVER_ERROR".equals(liveStatus)) {
            return "Server lỗi";
        }

        if ("INVALID_URL".equals(liveStatus)) {
            return "Sai URL";
        }

        return "Không xác minh";
    }

    private String buildCombinedStatusLabel(
            UrlCheckResult checked,
            GoogleIndexResult googleIndex,
            String liveStatus
    ) {
        String indexStatus = googleIndex == null ? "ERROR" : googleIndex.status;

        if ("INDEXED".equals(indexStatus) && "LIVE".equals(liveStatus)) {
            return "Tốt: URL đã index và link còn sống.";
        }

        if ("INDEXED".equals(indexStatus) && "REDIRECT".equals(liveStatus)) {
            return "Đã index. URL hiện có redirect, nên kiểm tra final URL.";
        }

        if ("INDEXED".equals(indexStatus) && "RESTRICTED".equals(liveStatus)) {
            return "Đã index, nhưng backend bị chặn khi kiểm tra link.";
        }

        if ("INDEXED".equals(indexStatus) && "DEAD".equals(liveStatus)) {
            return "Đã index trước đó, nhưng hiện link có dấu hiệu chết.";
        }

        if ("INDEXED".equals(indexStatus)) {
            return "Đã index, nhưng trạng thái sống/chết chưa xác minh chắc chắn.";
        }

        if ("NOT_INDEXED".equals(indexStatus) && "LIVE".equals(liveStatus)) {
            return "Link còn sống nhưng chưa thấy trên Google.";
        }

        if ("NOT_INDEXED".equals(indexStatus) && "RESTRICTED".equals(liveStatus)) {
            return "Chưa thấy index. Nền tảng/server có dấu hiệu chặn bot hoặc yêu cầu đăng nhập.";
        }

        if ("NOT_INDEXED".equals(indexStatus) && "DEAD".equals(liveStatus)) {
            return "Chưa thấy index và link có dấu hiệu chết.";
        }

        if ("NOT_INDEXED".equals(indexStatus) && "SERVER_ERROR".equals(liveStatus)) {
            return "Chưa thấy index. Server đang lỗi khi backend kiểm tra.";
        }

        if ("NOT_INDEXED".equals(indexStatus)) {
            return "Chưa thấy trên Google. Không nên xem là chưa index tuyệt đối.";
        }

        if ("VERIFY_NEEDED".equals(indexStatus)) {
            if ("LIVE".equals(liveStatus)) {
                return "Link sống, cần kiểm tra tay index.";
            }

            if ("RESTRICTED".equals(liveStatus)) {
                return "Chưa xác minh được index. Link có dấu hiệu chặn bot hoặc cần đăng nhập.";
            }

            return "Chưa xác minh được index tự động.";
        }

        if ("GOOGLE_BLOCK".equals(indexStatus)) {
            if ("LIVE".equals(liveStatus)) {
                return "Google chặn request check tự động, nhưng link còn sống.";
            }

            if ("RESTRICTED".equals(liveStatus)) {
                return "Google chặn request check, nền tảng cũng có dấu hiệu chặn bot.";
            }

            return "Google chặn request check tự động. Không kết luận là chưa index.";
        }

        if ("ERROR".equals(indexStatus)) {
            if ("LIVE".equals(liveStatus)) {
                return "Không check được index, nhưng link còn sống.";
            }

            if ("RESTRICTED".equals(liveStatus)) {
                return "Không check được index. Link có dấu hiệu bị chặn bot/login.";
            }

            return "Không check được index và link chưa xác minh chắc chắn.";
        }

        if (checked != null && checked.noindex) {
            return "Có noindex, khả năng index thấp.";
        }

        if (checked != null && checked.loginWall) {
            return "Có dấu hiệu cần đăng nhập.";
        }

        if (checked != null && checked.blocked) {
            return "Có dấu hiệu bị chặn bot/server.";
        }

        return "Cần xem lại tín hiệu.";
    }

    /*
     * Index Hub render đã tách sang IndexPingService.
     */
    public List<String> getIndexHubUrls() {
        return indexPingService.getIndexHubUrls();
    }

    public String renderIndexHubHtml() {
        return indexPingService.renderIndexHubHtml();
    }

    public String renderIndexHubSitemapXml() {
        return indexPingService.renderIndexHubSitemapXml();
    }

    public String renderIndexHubRssXml() {
        return indexPingService.renderIndexHubRssXml();
    }

    /*
     * Google index check helpers
     */
    private GoogleIndexResult checkGoogleIndexedSmart(String inputUrl, UrlCheckResult checked) {
        /*
         * Logic mới cho check index social/link ngoài:
         * - Không match mềm bằng title.
         * - Không match mềm bằng slug riêng lẻ.
         * - Không dùng nhiều query phụ dễ gây sai.
         * - Chỉ search Google theo kiểu: site:FULL_URL.
         * - Nếu Google trả kết quả có đúng host + path của URL thì xem là Đã Index.
         */
        String finalUrl = checked == null ? inputUrl : checked.finalUrl;

        List<String> queries = buildSimpleSiteIndexQueries(inputUrl, finalUrl);

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

            /*
             * Nếu Google báo không có kết quả thì qua query tiếp theo.
             */
            if (googleSearchHtmlHasNoResult(searchResult.html)) {
                continue;
            }

            /*
             * Ưu tiên 1:
             * Google result URL bóc ra được trùng inputUrl hoặc finalUrl.
             */
            if (matchesExactGoogleResultUrl(inputUrl, finalUrl, searchResult.resultUrls)) {
                return new GoogleIndexResult(
                        true,
                        "INDEXED",
                        "Google site:URL returned the exact target URL."
                );
            }

            /*
             * Ưu tiên 2:
             * HTML Google Search có tín hiệu kết quả thật và chứa đúng host + path.
             * Cách này giống kiểu check tay trên Google.
             */
            if (googleSearchHtmlContainsTargetUrl(searchResult.html, inputUrl)
                    || googleSearchHtmlContainsTargetUrl(searchResult.html, finalUrl)) {
                return new GoogleIndexResult(
                        true,
                        "INDEXED",
                        "Google site:URL returned a result matching the target URL host and path."
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
                "Google site:URL did not return matching results."
        );
    }

    private List<String> buildSimpleSiteIndexQueries(String inputUrl, String finalUrl) {
        Set<String> queries = new LinkedHashSet<>();

        String cleanInput = inputUrl == null ? "" : inputUrl.trim();
        String cleanFinal = finalUrl == null ? "" : finalUrl.trim();

        if (!cleanInput.isEmpty()) {
            queries.add("site:" + cleanInput);
        }

        /*
         * Nếu URL bị redirect sang finalUrl khác thì check thêm finalUrl.
         * Ví dụ x.com có thể đổi qua twitter.com hoặc ngược lại ở một số trường hợp.
         */
        if (!cleanFinal.isEmpty()
                && !normalizeUrlForCompare(cleanFinal).equals(normalizeUrlForCompare(cleanInput))) {
            queries.add("site:" + cleanFinal);
        }

        return new ArrayList<>(queries);
    }

    private boolean matchesExactGoogleResultUrl(String inputUrl, String finalUrl, List<String> resultUrls) {
        if (resultUrls == null || resultUrls.isEmpty()) {
            return false;
        }

        String normalizedInput = normalizeUrlForCompare(inputUrl);
        String normalizedFinal = normalizeUrlForCompare(finalUrl);

        for (String resultUrl : resultUrls) {
            String normalizedResult = normalizeUrlForCompare(resultUrl);

            if (!normalizedInput.isEmpty() && normalizedInput.equals(normalizedResult)) {
                return true;
            }

            if (!normalizedFinal.isEmpty() && normalizedFinal.equals(normalizedResult)) {
                return true;
            }
        }

        return false;
    }

    private boolean googleSearchHtmlContainsTargetUrl(String html, String targetUrl) {
        if (html == null || html.isBlank() || targetUrl == null || targetUrl.isBlank()) {
            return false;
        }

        if (!isValidHttpUrl(targetUrl)) {
            return false;
        }

        String lowerHtml = urlDecodeQuietly(html).toLowerCase();

        if (googleSearchHtmlHasNoResult(lowerHtml)) {
            return false;
        }

        /*
         * Tránh Google chỉ echo lại query trong ô search.
         * Phải có dấu hiệu trang kết quả thật.
         */
        boolean hasSearchResultSignal =
                lowerHtml.contains("<h3")
                        || lowerHtml.contains("/url?")
                        || lowerHtml.contains("data-ved")
                        || lowerHtml.contains("jsname=")
                        || lowerHtml.contains("class=\"g\"")
                        || lowerHtml.contains("yurubf");

        if (!hasSearchResultSignal) {
            return false;
        }

        String host = getHostQuietly(targetUrl);
        String path = normalizePathForCompare(getPathQuietly(targetUrl));
        String normalizedTarget = normalizeUrlForCompare(targetUrl);

        if (host.isEmpty()) {
            return false;
        }

        /*
         * Với profile URL dạng domain.com/user, path là thứ quan trọng.
         * Với homepage/profile root thì chỉ cần host.
         */
        if (path.isEmpty() || "/".equals(path)) {
            return lowerHtml.contains(host);
        }

        if (!normalizedTarget.isEmpty() && lowerHtml.contains(normalizedTarget)) {
            return true;
        }

        return lowerHtml.contains(host) && lowerHtml.contains(path);
    }

    private boolean googleSearchHtmlHasNoResult(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }

        String lower = urlDecodeQuietly(html).toLowerCase();

        return lower.contains("did not match any documents")
                || lower.contains("your search -")
                || lower.contains("không khớp với bất kỳ tài liệu nào")
                || lower.contains("khong khop voi bat ky tai lieu nao")
                || lower.contains("không tìm thấy kết quả nào")
                || lower.contains("khong tim thay ket qua nao")
                || lower.contains("không có kết quả nào")
                || lower.contains("khong co ket qua nao")
                || lower.contains("no results found");
    }


    /*
     * Giữ lại hàm cũ để nếu chỗ khác còn gọi thì không lỗi compile.
     * Nhưng processCheckIndex hiện dùng checkGoogleIndexedSmart().
     */
    private GoogleIndexResult checkGoogleIndexedByInputUrl(String inputUrl, String finalUrl) {
        UrlCheckResult temp = new UrlCheckResult();
        temp.originalUrl = inputUrl;
        temp.finalUrl = finalUrl == null || finalUrl.isBlank() ? inputUrl : finalUrl;
        temp.alive = false;
        temp.title = "";

        return checkGoogleIndexedSmart(inputUrl, temp);
    }

    private List<String> buildGoogleIndexQueriesSmart(String inputUrl, String finalUrl, String title) {
        Set<String> queries = new LinkedHashSet<>();

        String cleanInput = inputUrl == null ? "" : inputUrl.trim();
        String cleanFinal = finalUrl == null ? "" : finalUrl.trim();

        if (!cleanInput.isEmpty()) {
            queries.add("site:" + cleanInput);
            queries.add("\"" + cleanInput + "\"");

            String host = getHostQuietly(cleanInput);
            String path = getPathQuietly(cleanInput);

            if (!host.isEmpty() && !path.isEmpty()) {
                queries.add("site:" + host + " \"" + path + "\"");
            }

            String googleFileId = extractGoogleFileId(cleanInput);
            if (googleFileId != null && !googleFileId.isEmpty()) {
                queries.add("\"" + googleFileId + "\"");
                queries.add("inurl:" + googleFileId);

                if (host.contains("docs.google.com")) {
                    queries.add("site:docs.google.com \"" + googleFileId + "\"");
                }

                if (host.contains("drive.google.com")) {
                    queries.add("site:drive.google.com \"" + googleFileId + "\"");
                }
            }

            String slug = extractLastSlug(cleanInput);
            if (slug != null && slug.length() >= 4) {
                queries.add("inurl:" + slug);

                if (!host.isEmpty()) {
                    queries.add("site:" + host + " inurl:" + slug);
                }
            }
        }

        if (!cleanFinal.isEmpty() && !cleanFinal.equals(cleanInput)) {
            queries.add("site:" + cleanFinal);
            queries.add("\"" + cleanFinal + "\"");

            String finalHost = getHostQuietly(cleanFinal);
            String finalPath = getPathQuietly(cleanFinal);

            if (!finalHost.isEmpty() && !finalPath.isEmpty()) {
                queries.add("site:" + finalHost + " \"" + finalPath + "\"");
            }

            String finalFileId = extractGoogleFileId(cleanFinal);
            if (finalFileId != null && !finalFileId.isEmpty()) {
                queries.add("\"" + finalFileId + "\"");
                queries.add("inurl:" + finalFileId);
            }

            String finalSlug = extractLastSlug(cleanFinal);
            if (finalSlug != null && finalSlug.length() >= 4) {
                queries.add("inurl:" + finalSlug);

                if (!finalHost.isEmpty()) {
                    queries.add("site:" + finalHost + " inurl:" + finalSlug);
                }
            }
        }

        String cleanTitle = normalizeTitleForSearch(title);
        if (!cleanTitle.isEmpty()) {
            String host = getHostQuietly(cleanInput);

            queries.add("\"" + cleanTitle + "\"");

            if (!host.isEmpty()) {
                queries.add("site:" + host + " \"" + cleanTitle + "\"");
            }

            if (isGooglePropertyUrl(cleanInput)) {
                if (host.contains("sites.google.com")) {
                    queries.add("site:sites.google.com \"" + cleanTitle + "\"");
                } else if (host.contains("docs.google.com")) {
                    queries.add("site:docs.google.com \"" + cleanTitle + "\"");
                } else if (host.contains("drive.google.com")) {
                    queries.add("site:drive.google.com \"" + cleanTitle + "\"");
                }
            }
        }

        return new ArrayList<>(queries);
    }

    /*
     * Giữ lại hàm cũ để nếu có chỗ khác dùng thì không lỗi compile.
     */
    private List<String> buildGoogleIndexQueriesFromInputUrl(String inputUrl) {
        return buildGoogleIndexQueriesSmart(inputUrl, inputUrl, "");
    }

    private GoogleSearchResult runGoogleSearch(String query) {
        HttpURLConnection connection = null;

        try {
            String googleUrl = "https://www.google.com/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&num=20&hl=en";

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
                        "Google blocked the automated search request.",
                        html
                );
            }

            if (statusCode >= 400) {
                return new GoogleSearchResult(
                        "ERROR",
                        new ArrayList<>(),
                        "Google returned HTTP " + statusCode,
                        html
                );
            }

            List<String> resultUrls = extractGoogleResultUrls(html);

            return new GoogleSearchResult(
                    "OK",
                    resultUrls,
                    "Google search completed.",
                    html
            );

        } catch (Exception e) {
            return new GoogleSearchResult(
                    "ERROR",
                    new ArrayList<>(),
                    "Google search error: " + e.getMessage(),
                    ""
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

            /*
             * Với social URL có tracking hoặc redirect, đôi khi Google trả URL gần đúng.
             * So khớp mềm bằng slug cuối.
             */
            String inputSlug = extractLastSlug(inputUrl);
            String resultSlug = extractLastSlug(resultUrl);

            if (inputSlug != null
                    && resultSlug != null
                    && inputSlug.length() >= 8
                    && inputSlug.equals(resultSlug)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesGooglePropertySoft(
            String inputUrl,
            String finalUrl,
            String title,
            List<String> resultUrls
    ) {
        if (resultUrls == null || resultUrls.isEmpty()) {
            return false;
        }

        if (!isGooglePropertyUrl(inputUrl) && !isGooglePropertyUrl(finalUrl)) {
            return false;
        }

        String inputFileId = extractGoogleFileId(inputUrl);
        String finalFileId = extractGoogleFileId(finalUrl);

        String inputHost = getHostQuietly(inputUrl);
        String finalHost = getHostQuietly(finalUrl);

        String inputSlug = extractLastSlug(inputUrl);
        String finalSlug = extractLastSlug(finalUrl);

        String normalizedInputPath = normalizePathForCompare(getPathQuietly(inputUrl));
        String normalizedFinalPath = normalizePathForCompare(getPathQuietly(finalUrl));

        for (String resultUrl : resultUrls) {
            String resultHost = getHostQuietly(resultUrl);
            String resultFileId = extractGoogleFileId(resultUrl);
            String resultSlug = extractLastSlug(resultUrl);
            String normalizedResultPath = normalizePathForCompare(getPathQuietly(resultUrl));

            if (inputFileId != null && !inputFileId.isEmpty()) {
                if (inputFileId.equals(resultFileId) || resultUrl.contains(inputFileId)) {
                    return true;
                }
            }

            if (finalFileId != null && !finalFileId.isEmpty()) {
                if (finalFileId.equals(resultFileId) || resultUrl.contains(finalFileId)) {
                    return true;
                }
            }

            if (inputHost.contains("sites.google.com")
                    && resultHost.contains("sites.google.com")
                    && inputSlug != null
                    && resultSlug != null
                    && inputSlug.equals(resultSlug)) {
                return true;
            }

            if (finalHost.contains("sites.google.com")
                    && resultHost.contains("sites.google.com")
                    && finalSlug != null
                    && resultSlug != null
                    && finalSlug.equals(resultSlug)) {
                return true;
            }

            if (!normalizedInputPath.isEmpty()
                    && !normalizedResultPath.isEmpty()
                    && normalizedInputPath.equals(normalizedResultPath)) {
                return true;
            }

            if (!normalizedFinalPath.isEmpty()
                    && !normalizedResultPath.isEmpty()
                    && normalizedFinalPath.equals(normalizedResultPath)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesGoogleSearchHtml(
            String inputUrl,
            String finalUrl,
            String title,
            String html
    ) {
        if (html == null || html.isBlank()) {
            return false;
        }

        String lowerHtml = urlDecodeQuietly(html).toLowerCase();

        /*
         * Tránh false positive do Google echo lại query trong ô search.
         * Cần có tín hiệu trang kết quả thật.
         */
        boolean hasSearchResultSignal =
                lowerHtml.contains("<h3")
                        || lowerHtml.contains("/url?")
                        || lowerHtml.contains("data-ved")
                        || lowerHtml.contains("jsname=");

        if (!hasSearchResultSignal) {
            return false;
        }

        String normalizedInput = normalizeUrlForCompare(inputUrl);
        String normalizedFinal = normalizeUrlForCompare(finalUrl);

        if (!normalizedInput.isEmpty()
                && lowerHtml.contains(normalizedInput)
                && containsHostQuietly(lowerHtml, inputUrl)) {
            return true;
        }

        if (!normalizedFinal.isEmpty()
                && lowerHtml.contains(normalizedFinal)
                && containsHostQuietly(lowerHtml, finalUrl)) {
            return true;
        }

        String inputFileId = extractGoogleFileId(inputUrl);
        String finalFileId = extractGoogleFileId(finalUrl);

        if (inputFileId != null && inputFileId.length() >= 10) {
            String id = inputFileId.toLowerCase();

            if (lowerHtml.contains(id) && containsAnyGooglePropertyHost(lowerHtml)) {
                return true;
            }
        }

        if (finalFileId != null && finalFileId.length() >= 10) {
            String id = finalFileId.toLowerCase();

            if (lowerHtml.contains(id) && containsAnyGooglePropertyHost(lowerHtml)) {
                return true;
            }
        }

        String inputPath = normalizePathForCompare(getPathQuietly(inputUrl));
        String finalPath = normalizePathForCompare(getPathQuietly(finalUrl));

        if (!inputPath.isEmpty()
                && inputPath.length() >= 8
                && lowerHtml.contains(inputPath)
                && containsHostQuietly(lowerHtml, inputUrl)) {
            return true;
        }

        if (!finalPath.isEmpty()
                && finalPath.length() >= 8
                && lowerHtml.contains(finalPath)
                && containsHostQuietly(lowerHtml, finalUrl)) {
            return true;
        }

        String inputSlug = extractLastSlug(inputUrl);
        String finalSlug = extractLastSlug(finalUrl);

        if (inputSlug != null
                && inputSlug.length() >= 4
                && lowerHtml.contains(inputSlug)
                && containsHostQuietly(lowerHtml, inputUrl)) {
            return true;
        }

        if (finalSlug != null
                && finalSlug.length() >= 4
                && lowerHtml.contains(finalSlug)
                && containsHostQuietly(lowerHtml, finalUrl)) {
            return true;
        }

        String cleanTitle = normalizeTitleForSearch(title).toLowerCase();

        if (!cleanTitle.isEmpty()
                && cleanTitle.length() >= 12
                && lowerHtml.contains(cleanTitle)
                && (containsHostQuietly(lowerHtml, inputUrl) || containsHostQuietly(lowerHtml, finalUrl))) {
            return true;
        }

        return false;
    }

    private boolean containsAnyGooglePropertyHost(String html) {
        return html.contains("docs.google.com")
                || html.contains("drive.google.com")
                || html.contains("sites.google.com");
    }

    private boolean containsHostQuietly(String html, String url) {
        String host = getHostQuietly(url);

        if (host.isEmpty()) {
            return false;
        }

        return html.contains(host.toLowerCase());
    }


    private boolean isLikePionStyleSiteIndexed(
            String query,
            String inputUrl,
            String finalUrl,
            String html
    ) {
        if (query == null || html == null || html.isBlank()) {
            return false;
        }

        String q = query.trim().toLowerCase();
        String input = inputUrl == null ? "" : inputUrl.trim().toLowerCase();
        String fin = finalUrl == null ? "" : finalUrl.trim().toLowerCase();

        boolean isSiteInput = q.equals("site:" + input);
        boolean isSiteFinal = !fin.isEmpty() && q.equals("site:" + fin);

        if (!isSiteInput && !isSiteFinal) {
            return false;
        }

        return googleHtmlLooksIndexed(html, inputUrl, finalUrl);
    }

    private boolean googleHtmlLooksIndexed(String html, String inputUrl, String finalUrl) {
        if (html == null || html.isBlank()) {
            return false;
        }

        String lower = urlDecodeQuietly(html).toLowerCase();

        boolean blocked =
                lower.contains("our systems have detected unusual traffic")
                        || lower.contains("unusual traffic from your computer network")
                        || lower.contains("/sorry/index")
                        || lower.contains("to continue, please type the characters below")
                        || lower.contains("captcha")
                        || lower.contains("about this page");

        if (blocked) {
            return false;
        }

        boolean noResult =
                lower.contains("did not match any documents")
                        || lower.contains("your search -")
                        || lower.contains("không khớp với bất kỳ tài liệu nào")
                        || lower.contains("khong khop voi bat ky tai lieu nao")
                        || lower.contains("không tìm thấy kết quả nào")
                        || lower.contains("khong tim thay ket qua nao")
                        || lower.contains("không có kết quả nào")
                        || lower.contains("khong co ket qua nao")
                        || lower.contains("no results found");

        if (noResult) {
            return false;
        }

        String inputFileId = extractGoogleFileId(inputUrl);
        String finalFileId = extractGoogleFileId(finalUrl);

        if (inputFileId != null && inputFileId.length() >= 10
                && lower.contains(inputFileId.toLowerCase())) {
            return true;
        }

        if (finalFileId != null && finalFileId.length() >= 10
                && lower.contains(finalFileId.toLowerCase())) {
            return true;
        }

        String inputPath = normalizePathForCompare(getPathQuietly(inputUrl));
        String finalPath = normalizePathForCompare(getPathQuietly(finalUrl));

        if (!inputPath.isEmpty() && inputPath.length() >= 8 && lower.contains(inputPath)) {
            return true;
        }

        if (!finalPath.isEmpty() && finalPath.length() >= 8 && lower.contains(finalPath)) {
            return true;
        }

        String inputHost = getHostQuietly(inputUrl);
        String finalHost = getHostQuietly(finalUrl);

        boolean hasHost =
                (!inputHost.isEmpty() && lower.contains(inputHost))
                        || (!finalHost.isEmpty() && lower.contains(finalHost));

        boolean hasResultSignal =
                lower.contains("<h3")
                        || lower.contains("/url?")
                        || lower.contains("data-ved")
                        || lower.contains("jsname=")
                        || lower.contains("class=\"g\"")
                        || lower.contains("class=\"yurubf")
                        || lower.contains("yuRUbf".toLowerCase());

        return hasResultSignal && hasHost;
    }

    private String buildIndexedNote(String inputUrl) {
        if (isGoogleFileUrl(inputUrl)) {
            return "URL was found in Google organic results by matching the Google file ID from the input URL.";
        }

        if (isGooglePropertyUrl(inputUrl)) {
            return "URL was found in Google organic results by matching Google property signals.";
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

    private String extractLastSlug(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(urlDecodeQuietly(url));
            String path = uri.getPath();

            if (path == null || path.isBlank()) {
                return null;
            }

            String cleanPath = path.replaceAll("/+$", "");
            int lastSlash = cleanPath.lastIndexOf("/");

            String slug = lastSlash >= 0
                    ? cleanPath.substring(lastSlash + 1)
                    : cleanPath;

            slug = slug.trim();

            if (slug.isEmpty()) {
                return null;
            }

            /*
             * Bỏ vài đuôi phổ biến không có giá trị so khớp.
             */
            if ("posts".equalsIgnoreCase(slug)
                    || "status".equalsIgnoreCase(slug)
                    || "profile".equalsIgnoreCase(slug)
                    || "activity".equalsIgnoreCase(slug)
                    || "edit".equalsIgnoreCase(slug)
                    || "view".equalsIgnoreCase(slug)
                    || "preview".equalsIgnoreCase(slug)
                    || "pub".equalsIgnoreCase(slug)
                    || "viewform".equalsIgnoreCase(slug)) {
                return null;
            }

            return slug.toLowerCase();
        } catch (Exception e) {
            return null;
        }
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

    private boolean isGooglePropertyUrl(String url) {
        String host = getHostQuietly(url);

        return host.contains("docs.google.com")
                || host.contains("drive.google.com")
                || host.contains("sites.google.com");
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

    private String getPathQuietly(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            return path == null ? "" : path;
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizePathForCompare(String path) {
        if (path == null) {
            return "";
        }

        return path.toLowerCase()
                .replaceAll("/edit$", "")
                .replaceAll("/view$", "")
                .replaceAll("/preview$", "")
                .replaceAll("/pub$", "")
                .replaceAll("/viewform$", "")
                .replaceAll("/+$", "")
                .trim();
    }

    private String normalizeTitleForSearch(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        String cleaned = title
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.length() > 90) {
            cleaned = cleaned.substring(0, 90).trim();
        }

        return cleaned;
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
            result.httpStatus = "INVALID";
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
            connection.setRequestProperty("Cache-Control", "no-cache");
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
                || lower.contains("verify you are human")
                || lower.contains("checkpoint")
                || lower.contains("temporarily blocked")
                || lower.contains("unusual traffic")
                || lower.contains("rate limit")
                || lower.contains("too many requests");
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
                        || lower.contains("please log in")
                        || lower.contains("you must log in")
                        || lower.contains("join facebook")
                        || lower.contains("create new account")
                        || lower.contains("see more on facebook");

        return hasLoginText && html.length() < 120000;
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
        String html;

        GoogleSearchResult(String status, List<String> resultUrls, String note) {
            this(status, resultUrls, note, "");
        }

        GoogleSearchResult(String status, List<String> resultUrls, String note, String html) {
            this.status = status;
            this.resultUrls = resultUrls;
            this.note = note;
            this.html = html == null ? "" : html;
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