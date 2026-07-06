package com.seo.seotool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SupabaseService {

    private static final int LIMIT = 1500;

    private static final String URL_TASKS_TABLE = "url_tasks";
    private static final String PING_LOGS_TABLE = "ping_logs";
    private static final String BOT_LOGS_TABLE = "bot_logs";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.secret}")
    private String supabaseSecret;

    public void saveOrUpdateUrl(String url, String httpStatus, String pingStatus, String discoveryStatus) {
        try {
            upsertUrlTask(url, "active");
            Long taskId = getUrlTaskId(url);

            if (taskId != null) {
                insertPingLog(taskId, url, "manual", true, null, pingStatus);
                incrementSubmitCount(url, httpStatus, pingStatus);
            }
        } catch (Exception e) {
            System.out.println("SUPABASE SAVE ERROR: " + e.getMessage());
        }
    }

    public void addUrlOnly(String url) {
        try {
            upsertUrlTask(url, "pending");
        } catch (Exception e) {
            System.out.println("SUPABASE ADD URL ERROR: " + e.getMessage());
        }
    }

    public List<String> getLatestUrls() {
        List<String> urls = new ArrayList<>();
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + URL_TASKS_TABLE
                    + "?select=url"
                    + "&order=updated_at.desc"
                    + "&limit=" + LIMIT;

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                System.out.println("SUPABASE GET URLS STATUS: " + status);
                System.out.println("SUPABASE GET URLS BODY: " + readBody(connection, status));
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
        } catch (Exception e) {
            System.out.println("SUPABASE GET URLS ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return urls;
    }

    public List<Map<String, Object>> getDueUrlTasks() {
        List<Map<String, Object>> rows = new ArrayList<>();
        HttpURLConnection connection = null;

        try {
            String now = OffsetDateTime.now().toString();

            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + URL_TASKS_TABLE
                    + "?select=id,url,submit_count,next_ping_at,last_ping_at"
                    + "&next_ping_at=lte." + encode(now)
                    + "&order=next_ping_at.asc"
                    + "&limit=100";

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                System.out.println("SUPABASE DUE URLS STATUS: " + status);
                System.out.println("SUPABASE DUE URLS BODY: " + readBody(connection, status));
                return rows;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray()) {
                for (JsonNode item : root) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", item.path("id").asLong());
                    row.put("url", item.path("url").asText(""));
                    row.put("submit_count", item.path("submit_count").asInt(0));
                    row.put("next_ping_at", item.path("next_ping_at").asText(""));
                    row.put("last_ping_at", item.path("last_ping_at").asText(""));
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            System.out.println("SUPABASE GET DUE URLS ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return rows;
    }

    public void markPingResult(long taskId, String url, boolean success, Integer responseCode, String message) {
        try {
            insertPingLog(taskId, url, "auto", success, responseCode, message);

            int currentCount = getSubmitCount(url);
            int nextCount = currentCount + 1;

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime nextPingAt = calculateNextPingAt(nextCount);

            String json = "{"
                    + "\"status\":\"active\","
                    + "\"submit_count\":" + nextCount + ","
                    + "\"last_ping_at\":\"" + escapeJson(now.toString()) + "\","
                    + "\"next_ping_at\":\"" + escapeJson(nextPingAt.toString()) + "\""
                    + "}";

            patchById(URL_TASKS_TABLE, taskId, json);
        } catch (Exception e) {
            System.out.println("SUPABASE MARK PING ERROR: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getUrlStatus() {
        List<Map<String, Object>> rows = new ArrayList<>();
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + URL_TASKS_TABLE
                    + "?select=*"
                    + "&order=updated_at.desc"
                    + "&limit=" + LIMIT;

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                System.out.println("SUPABASE URL STATUS STATUS: " + status);
                System.out.println("SUPABASE URL STATUS BODY: " + readBody(connection, status));
                return rows;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray()) {
                for (JsonNode item : root) {
                    rows.add(jsonNodeToMap(item));
                }
            }
        } catch (Exception e) {
            System.out.println("SUPABASE URL STATUS ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return rows;
    }

    public List<Map<String, Object>> getBotLogs() {
        List<Map<String, Object>> rows = new ArrayList<>();
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + BOT_LOGS_TABLE
                    + "?select=*"
                    + "&order=visited_at.desc"
                    + "&limit=300";

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                System.out.println("SUPABASE BOT LOG STATUS: " + status);
                System.out.println("SUPABASE BOT LOG BODY: " + readBody(connection, status));
                return rows;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray()) {
                for (JsonNode item : root) {
                    rows.add(jsonNodeToMap(item));
                }
            }
        } catch (Exception e) {
            System.out.println("SUPABASE BOT LOG ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return rows;
    }

    public void saveBotLog(String url, String userAgent, String ip, String referer) {
        try {
            boolean isGooglebot = isGooglebot(userAgent);
            String botName = detectBotName(userAgent);
            Long taskId = getUrlTaskId(url);

            String json = "{"
                    + "\"url\":\"" + escapeJson(url) + "\","
                    + "\"user_agent\":\"" + escapeJson(userAgent) + "\","
                    + "\"ip\":\"" + escapeJson(ip) + "\","
                    + "\"referer\":\"" + escapeJson(referer) + "\","
                    + "\"is_googlebot\":" + isGooglebot + ","
                    + "\"bot_name\":\"" + escapeJson(botName) + "\","
                    + "\"visited_at\":\"" + escapeJson(OffsetDateTime.now().toString()) + "\"";

            if (taskId != null) {
                json += ",\"url_task_id\":" + taskId;
            }

            json += "}";

            postJson(BOT_LOGS_TABLE, json);

            if (isGooglebot && taskId != null) {
                updateGooglebotStats(taskId, userAgent, ip);
            }
        } catch (Exception e) {
            System.out.println("SUPABASE SAVE BOT LOG ERROR: " + e.getMessage());
        }
    }

    private void upsertUrlTask(String url, String statusValue) throws Exception {
        Long existingId = getUrlTaskId(url);

        if (existingId != null) {
            String json = "{"
                    + "\"status\":\"" + escapeJson(statusValue) + "\""
                    + "}";

            patchById(URL_TASKS_TABLE, existingId, json);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        String json = "{"
                + "\"url\":\"" + escapeJson(url) + "\","
                + "\"status\":\"" + escapeJson(statusValue) + "\","
                + "\"submit_count\":0,"
                + "\"next_ping_at\":\"" + escapeJson(now.toString()) + "\","
                + "\"created_at\":\"" + escapeJson(now.toString()) + "\","
                + "\"updated_at\":\"" + escapeJson(now.toString()) + "\""
                + "}";

        postJson(URL_TASKS_TABLE, json);
    }

    private void incrementSubmitCount(String url, String httpStatus, String pingStatus) throws Exception {
        Long id = getUrlTaskId(url);

        if (id == null) {
            return;
        }

        int currentCount = getSubmitCount(url);
        int nextCount = currentCount + 1;

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nextPingAt = calculateNextPingAt(nextCount);

        String json = "{"
                + "\"status\":\"active\","
                + "\"submit_count\":" + nextCount + ","
                + "\"last_ping_at\":\"" + escapeJson(now.toString()) + "\","
                + "\"next_ping_at\":\"" + escapeJson(nextPingAt.toString()) + "\","
                + "\"last_checked_at\":\"" + escapeJson(now.toString()) + "\""
                + "}";

        patchById(URL_TASKS_TABLE, id, json);
    }

    private void insertPingLog(long taskId, String url, String pingType, boolean success, Integer responseCode, String message) throws Exception {
        String json = "{"
                + "\"url_task_id\":" + taskId + ","
                + "\"url\":\"" + escapeJson(url) + "\","
                + "\"ping_type\":\"" + escapeJson(pingType) + "\","
                + "\"success\":" + success + ","
                + "\"message\":\"" + escapeJson(message) + "\"";

        if (responseCode != null) {
            json += ",\"response_code\":" + responseCode;
        }

        json += "}";

        postJson(PING_LOGS_TABLE, json);
    }

    private Long getUrlTaskId(String url) {
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + URL_TASKS_TABLE
                    + "?select=id"
                    + "&url=eq." + encode(url)
                    + "&limit=1";

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                return null;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray() && root.size() > 0) {
                return root.get(0).path("id").asLong();
            }
        } catch (Exception e) {
            System.out.println("SUPABASE GET TASK ID ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    private int getSubmitCount(String url) {
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + URL_TASKS_TABLE
                    + "?select=submit_count"
                    + "&url=eq." + encode(url)
                    + "&limit=1";

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                return 0;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray() && root.size() > 0) {
                return root.get(0).path("submit_count").asInt(0);
            }
        } catch (Exception e) {
            System.out.println("SUPABASE GET SUBMIT COUNT ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return 0;
    }

    private void updateGooglebotStats(long taskId, String userAgent, String ip) throws Exception {
        int currentVisitCount = getGooglebotVisitCount(taskId);
        int nextVisitCount = currentVisitCount + 1;

        String json = "{"
                + "\"last_googlebot_visit\":\"" + escapeJson(OffsetDateTime.now().toString()) + "\","
                + "\"googlebot_visit_count\":" + nextVisitCount + ","
                + "\"last_user_agent\":\"" + escapeJson(userAgent) + "\","
                + "\"last_ip\":\"" + escapeJson(ip) + "\""
                + "}";

        patchById(URL_TASKS_TABLE, taskId, json);
    }

    private int getGooglebotVisitCount(long taskId) {
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + URL_TASKS_TABLE
                    + "?select=googlebot_visit_count"
                    + "&id=eq." + taskId
                    + "&limit=1";

            connection = openConnection(endpoint, "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                return 0;
            }

            JsonNode root = objectMapper.readTree(connection.getInputStream());

            if (root.isArray() && root.size() > 0) {
                return root.get(0).path("googlebot_visit_count").asInt(0);
            }
        } catch (Exception e) {
            System.out.println("SUPABASE GET BOT COUNT ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return 0;
    }

    private OffsetDateTime calculateNextPingAt(int submitCount) {
        OffsetDateTime now = OffsetDateTime.now();

        if (submitCount <= 1) {
            return now.plus(6, ChronoUnit.HOURS);
        }

        if (submitCount <= 3) {
            return now.plus(1, ChronoUnit.DAYS);
        }

        if (submitCount <= 5) {
            return now.plus(3, ChronoUnit.DAYS);
        }

        if (submitCount <= 8) {
            return now.plus(7, ChronoUnit.DAYS);
        }

        return now.plus(14, ChronoUnit.DAYS);
    }

    private boolean isGooglebot(String userAgent) {
        if (userAgent == null) {
            return false;
        }

        String ua = userAgent.toLowerCase();

        return ua.contains("googlebot")
                || ua.contains("google-inspectiontool")
                || ua.contains("googleother")
                || ua.contains("adsbot-google");
    }

    private String detectBotName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase();

        if (ua.contains("google-inspectiontool")) {
            return "Google-InspectionTool";
        }

        if (ua.contains("googlebot")) {
            return "Googlebot";
        }

        if (ua.contains("googleother")) {
            return "GoogleOther";
        }

        if (ua.contains("adsbot-google")) {
            return "AdsBot-Google";
        }

        return "User/Other";
    }

    private void postJson(String table, String json) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = openConnection(cleanBaseUrl() + "/rest/v1/" + table, "POST");
            connection.setRequestProperty("Prefer", "return=representation");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String body = readBody(connection, status);

            System.out.println("SUPABASE POST " + table + " STATUS: " + status);
            System.out.println("SUPABASE POST " + table + " BODY: " + body);

            if (status < 200 || status >= 300) {
                throw new RuntimeException("Post failed with status " + status + ": " + body);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void patchById(String table, long id, String json) throws Exception {
        HttpURLConnection connection = null;

        try {
            String endpoint = cleanBaseUrl()
                    + "/rest/v1/" + table
                    + "?id=eq." + id;

            connection = openConnection(endpoint, "PATCH");
            connection.setRequestProperty("Prefer", "return=representation");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String body = readBody(connection, status);

            System.out.println("SUPABASE PATCH " + table + " STATUS: " + status);
            System.out.println("SUPABASE PATCH " + table + " BODY: " + body);

            if (status < 200 || status >= 300) {
                throw new RuntimeException("Patch failed with status " + status + ": " + body);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(String endpoint, String method) throws Exception {
        String secret = cleanSecret();

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", secret);
        connection.setRequestProperty("Authorization", "Bearer " + secret);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        return connection;
    }

    private String readBody(HttpURLConnection connection, int statusCode) {
        try {
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                return "";
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

            StringBuilder body = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            reader.close();
            return body.toString();
        } catch (Exception e) {
            return "READ_BODY_ERROR: " + e.getMessage();
        }
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();

            if (value == null || value.isNull()) {
                map.put(field.getKey(), null);
            } else if (value.isNumber()) {
                map.put(field.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                map.put(field.getKey(), value.asBoolean());
            } else {
                map.put(field.getKey(), value.asText());
            }
        }

        return map;
    }

    private String cleanBaseUrl() {
        String base = supabaseUrl == null ? "" : supabaseUrl.trim().replaceAll("/+$", "");

        if (base.endsWith("/rest/v1")) {
            base = base.substring(0, base.length() - "/rest/v1".length());
        }

        return base;
    }

    private String cleanSecret() {
        if (supabaseSecret == null) {
            return "";
        }

        return supabaseSecret
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .replace("\"", "")
                .trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}