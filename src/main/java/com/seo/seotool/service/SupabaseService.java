package com.seo.seotool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SupabaseService {

    private static final int LIMIT = 1500;
    private static final String TABLE = "seo_ping_monitor_records";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.secret}")
    private String supabaseSecret;

    public void saveOrUpdateUrl(String url, String httpStatus, String pingStatus, String discoveryStatus) {
        try {
            int currentPingCount = getCurrentPingCount(url);

            if (currentPingCount > 0) {
                updateUrl(url, httpStatus, pingStatus, discoveryStatus, currentPingCount + 1);
            } else {
                insertUrl(url, httpStatus, pingStatus, discoveryStatus);
            }
        } catch (Exception ignored) {
        }
    }

    public List<String> getLatestUrls() {
        List<String> urls = new ArrayList<>();
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + TABLE
                    + "?select=url"
                    + "&order=last_pinged_at.desc"
                    + "&limit=" + LIMIT;

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                return urls;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray()) {
                for (JsonNode item : root) {
                    JsonNode urlNode = item.get("url");

                    if (urlNode != null && !urlNode.asText().isBlank()) {
                        urls.add(urlNode.asText());
                    }
                }
            }
        } catch (Exception ignored) {
            return urls;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return urls;
    }

    private void insertUrl(String url, String httpStatus, String pingStatus, String discoveryStatus) throws Exception {
        String now = OffsetDateTime.now().toString();

        String json = "{"
                + "\"url\":\"" + escapeJson(url) + "\","
                + "\"http_status\":\"" + escapeJson(httpStatus) + "\","
                + "\"ping_status\":\"" + escapeJson(pingStatus) + "\","
                + "\"discovery_status\":\"" + escapeJson(discoveryStatus) + "\","
                + "\"ping_count\":1,"
                + "\"created_at\":\"" + escapeJson(now) + "\","
                + "\"last_pinged_at\":\"" + escapeJson(now) + "\""
                + "}";

        HttpURLConnection connection = null;

        try {
            connection = openConnection(cleanBaseUrl() + "/rest/v1/" + TABLE, "POST");
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            connection.getResponseCode();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void updateUrl(String url, String httpStatus, String pingStatus, String discoveryStatus, int pingCount) throws Exception {
        String now = OffsetDateTime.now().toString();

        String json = "{"
                + "\"http_status\":\"" + escapeJson(httpStatus) + "\","
                + "\"ping_status\":\"" + escapeJson(pingStatus) + "\","
                + "\"discovery_status\":\"" + escapeJson(discoveryStatus) + "\","
                + "\"ping_count\":" + pingCount + ","
                + "\"last_pinged_at\":\"" + escapeJson(now) + "\""
                + "}";

        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + TABLE
                    + "?url=eq." + encode(url);

            connection = openConnection(endpoint, "PATCH");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            connection.getResponseCode();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private int getCurrentPingCount(String url) {
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + TABLE
                    + "?select=ping_count"
                    + "&url=eq." + encode(url)
                    + "&limit=1";

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                return 0;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray() && root.size() > 0) {
                JsonNode countNode = root.get(0).get("ping_count");

                if (countNode != null && countNode.isNumber()) {
                    return countNode.asInt();
                }
            }
        } catch (Exception ignored) {
            return 0;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return 0;
    }

    private HttpURLConnection openConnection(String endpoint, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", supabaseSecret);
        connection.setRequestProperty("Authorization", "Bearer " + supabaseSecret);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        return connection;
    }

    private String cleanBaseUrl() {
        String base = supabaseUrl == null ? "" : supabaseUrl.trim().replaceAll("/+$", "");

        if (base.endsWith("/rest/v1")) {
            base = base.substring(0, base.length() - "/rest/v1".length());
        }

        return base;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}