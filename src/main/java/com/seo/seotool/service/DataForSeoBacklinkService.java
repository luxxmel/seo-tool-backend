package com.seo.seotool.service;

import com.seo.seotool.dto.AuditResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataForSeoBacklinkService {

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 25000;

    @Value("${dataforseo.login:}")
    private String login;

    @Value("${dataforseo.password:}")
    private String password;

    public AuditResponse.BacklinkProfile fetchBacklinkProfile(String targetUrl) {
        AuditResponse.BacklinkProfile backlink = buildDefaultProfile();

        if (isBlank(login) || isBlank(password)) {
            backlink.setDataSource("DataForSEO not configured");
            backlink.setNote("Thiếu DATAFORSEO_LOGIN hoặc DATAFORSEO_PASSWORD trong Render Environment.");
            return backlink;
        }

        String target = normalizeTarget(targetUrl);

        if (isBlank(target)) {
            backlink.setDataSource("DataForSEO");
            backlink.setNote("Target không hợp lệ.");
            return backlink;
        }

        try {
            String body = "[{\"target\":\"" + escapeJson(target) + "\",\"internal_list_limit\":1,\"backlinks_status_type\":\"live\"}]";

            String json = postJson(
                    "https://api.dataforseo.com/v3/backlinks/summary/live",
                    body
            );

            if (isBlank(json)) {
                backlink.setDataSource("DataForSEO");
                backlink.setNote("DataForSEO trả response rỗng.");
                return backlink;
            }

            String statusMessage = extractString(json, "status_message");
            Integer statusCode = extractInteger(json, "status_code");

            if (statusCode != null && statusCode >= 40000) {
                backlink.setDataSource("DataForSEO error");
                backlink.setNote("DataForSEO lỗi: " + safeText(statusMessage));
                return backlink;
            }

            Long backlinks = extractLong(json, "backlinks");
            Long referringDomains = extractLong(json, "referring_domains");
            Long dofollow = extractLong(json, "dofollow");
            Long nofollow = extractLong(json, "nofollow");
            Long rank = extractLong(json, "rank");

            backlink.setAuthorityScore(rank == null ? "N/A" : String.valueOf(rank));
            backlink.setDomainRating(rank == null ? "N/A" : String.valueOf(rank));
            backlink.setUrlRating("N/A");
            backlink.setBacklinks(backlinks == null ? "N/A" : formatNumber(backlinks));
            backlink.setReferringDomains(referringDomains == null ? "N/A" : formatNumber(referringDomains));
            backlink.setDofollowBacklinks(dofollow == null ? "N/A" : formatNumber(dofollow));
            backlink.setNofollowBacklinks(nofollow == null ? "N/A" : formatNumber(nofollow));
            backlink.setNewBacklinks("N/A");
            backlink.setLostBacklinks("N/A");
            backlink.setDataSource("DataForSEO");
            backlink.setNote("Dữ liệu backlink lấy từ DataForSEO Backlinks Summary API.");

            return backlink;

        } catch (Exception e) {
            backlink.setDataSource("DataForSEO error");
            backlink.setNote("Lỗi khi gọi DataForSEO: " + safeText(e.getMessage()));
            return backlink;
        }
    }

    private String postJson(String apiUrl, String body) throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setDoOutput(true);

            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Basic " + buildBasicAuth());

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = connection.getResponseCode();

            InputStream stream = statusCode >= 200 && statusCode < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                return "";
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            return response.toString();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildBasicAuth() {
        String raw = login + ":" + password;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private AuditResponse.BacklinkProfile buildDefaultProfile() {
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
        backlink.setNote("Chưa có dữ liệu backlink.");
        return backlink;
    }

    private String normalizeTarget(String targetUrl) {
        if (isBlank(targetUrl)) {
            return "";
        }

        try {
            String value = targetUrl.trim();

            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://" + value;
            }

            URI uri = URI.create(value);
            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                return value
                        .replace("https://", "")
                        .replace("http://", "")
                        .replaceFirst("^www\\.", "")
                        .replaceAll("/$", "")
                        .trim()
                        .toLowerCase();
            }

            return host
                    .replaceFirst("^www\\.", "")
                    .toLowerCase();

        } catch (Exception e) {
            return targetUrl
                    .replace("https://", "")
                    .replace("http://", "")
                    .replaceFirst("^www\\.", "")
                    .replaceAll("/$", "")
                    .trim()
                    .toLowerCase();
        }
    }

    private Long extractLong(String json, String key) {
        try {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
            Matcher matcher = pattern.matcher(json);

            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private Integer extractInteger(String json, String key) {
        Long value = extractLong(json, key);

        if (value == null) {
            return null;
        }

        return value.intValue();
    }

    private String extractString(String json, String key) {
        try {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"");
            Matcher matcher = pattern.matcher(json);

            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatNumber(Long value) {
        if (value == null) {
            return "N/A";
        }

        return String.format("%,d", value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String safeText(String value) {
        if (isBlank(value)) {
            return "Không rõ lỗi.";
        }

        if (value.length() > 180) {
            return value.substring(0, 180) + "...";
        }

        return value;
    }
}