package com.seo.seotool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seo.seotool.dto.CheckIndexRequest;
import com.seo.seotool.dto.CheckIndexResponse;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class WebsiteAuditService {

    public List<CheckIndexResponse> checkIndexBySerper(CheckIndexRequest request) {
        List<CheckIndexResponse> results = new ArrayList<>();

        if (request == null || request.getUrls() == null || request.getUrls().isEmpty()) {
            return results;
        }

        String apiKey = safeTrim(request.getApiKey());

        for (String rawUrl : request.getUrls()) {
            String targetUrl = safeTrim(rawUrl);

            if (targetUrl.isBlank()) {
                continue;
            }

            if (!isValidHttpUrl(targetUrl)) {
                results.add(buildIndexResult(
                        targetUrl,
                        targetUrl,
                        "INVALID",
                        false,
                        false,
                        "ERROR",
                        "INVALID_URL",
                        "",
                        "URL sai định dạng.",
                        false,
                        false,
                        false
                ));
                continue;
            }

            if (apiKey.isBlank()) {
                results.add(buildIndexResult(
                        targetUrl,
                        targetUrl,
                        "ERROR",
                        false,
                        null,
                        "ERROR",
                        "UNKNOWN",
                        "",
                        "Thiếu API key Serper.",
                        false,
                        false,
                        false
                ));
                continue;
            }

            try {
                results.add(checkOneUrlBySerper(apiKey, targetUrl));
                sleepQuietly(180);
            } catch (Exception e) {
                results.add(buildIndexResult(
                        targetUrl,
                        targetUrl,
                        "ERROR",
                        false,
                        null,
                        "ERROR",
                        "UNKNOWN",
                        "",
                        "Lỗi Serper: " + safeMessage(e.getMessage()),
                        false,
                        false,
                        false
                ));
            }
        }

        return results;
    }

    private CheckIndexResponse checkOneUrlBySerper(String apiKey, String targetUrl) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpURLConnection connection = null;

        try {
            URL serperUrl = new URL("https://google.serper.dev/search");
            connection = (HttpURLConnection) serperUrl.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-API-KEY", apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);

            String body = mapper.createObjectNode()
                    .put("q", "site:" + targetUrl)
                    .put("gl", "vn")
                    .put("hl", "vi")
                    .put("num", 10)
                    .toString();

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = connection.getResponseCode();

            if (statusCode < 200 || statusCode >= 300) {
                return buildIndexResult(
                        targetUrl,
                        targetUrl,
                        String.valueOf(statusCode),
                        false,
                        null,
                        "ERROR",
                        "UNKNOWN",
                        "",
                        "Serper trả lỗi HTTP " + statusCode + ". Kiểm tra API key hoặc quota.",
                        false,
                        true,
                        false
                );
            }

            JsonNode root = mapper.readTree(connection.getInputStream());
            JsonNode organic = root.path("organic");

            if (!organic.isArray() || organic.isEmpty()) {
                return buildIndexResult(
                        targetUrl,
                        targetUrl,
                        "200",
                        true,
                        false,
                        "NOT_INDEXED",
                        "LIVE",
                        "",
                        "Chưa thấy URL trong Google qua Serper.",
                        false,
                        false,
                        false
                );
            }

            for (JsonNode organicItem : organic) {
                String foundUrl = organicItem.path("link").asText("");
                String title = organicItem.path("title").asText("");

                if (isSameUrlOrEquivalent(targetUrl, foundUrl)) {
                    return buildIndexResult(
                            targetUrl,
                            foundUrl,
                            "200",
                            true,
                            true,
                            "INDEXED",
                            "LIVE",
                            title,
                            "Đã thấy URL trên Google.",
                            false,
                            false,
                            false
                    );
                }
            }

            return buildIndexResult(
                    targetUrl,
                    targetUrl,
                    "200",
                    true,
                    false,
                    "NOT_INDEXED",
                    "LIVE",
                    "",
                    "Có kết quả Google nhưng chưa khớp đúng URL.",
                    false,
                    false,
                    false
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private CheckIndexResponse buildIndexResult(
            String url,
            String finalUrl,
            String httpStatus,
            Boolean alive,
            Boolean indexed,
            String indexStatus,
            String liveStatus,
            String title,
            String note,
            Boolean noindex,
            Boolean blocked,
            Boolean loginWall
    ) {
        return new CheckIndexResponse(
                url,
                finalUrl,
                httpStatus,
                alive,
                indexed,
                indexStatus,
                liveStatus,
                title,
                note,
                noindex,
                blocked,
                loginWall,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        );
    }

    private boolean isSameUrlOrEquivalent(String targetUrl, String foundUrl) {
        String target = normalizeUrlForCompare(targetUrl);
        String found = normalizeUrlForCompare(foundUrl);

        if (target.isBlank() || found.isBlank()) {
            return false;
        }

        return target.equals(found)
                || found.startsWith(target)
                || target.startsWith(found);
    }

    private String normalizeUrlForCompare(String url) {
        if (url == null) {
            return "";
        }

        return url.toLowerCase()
                .replace("https://", "")
                .replace("http://", "")
                .replace("www.", "")
                .replace("mobile.", "")
                .replace("m.facebook.com/", "facebook.com/")
                .replace("twitter.com/", "x.com/")
                .replaceAll("#.*$", "")
                .replaceAll("\\?.*$", "")
                .replaceAll("/$", "")
                .trim();
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

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignored) {
        }
    }
}