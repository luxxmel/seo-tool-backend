package com.seo.seotool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seo.seotool.dto.LeadScanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LeadScannerService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+84|0)([\\s.\\-()]?\\d){8,12}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern WEBSITE_PATTERN = Pattern.compile("(?i)(https?://)?(www\\.)?[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]*)?");
    private static final Pattern TAX_PATTERN = Pattern.compile("(?i)(MST|Ma so thue|Mã số thuế|Tax code)[:\\s]*([0-9\\-]{10,15})");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final Map<String, LeadScanResponse> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LeadScanResponse> eldest) {
            return size() > 100;
        }
    };

    @Value("${gemini.api.keys:${GEMINI_API_KEYS:}}")
    private String geminiApiKeys;

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${gemini.model:${GEMINI_MODEL:gemini-2.5-flash}}")
    private String geminiModel;

    public LeadScanResponse scanImage(MultipartFile image) {
        LeadScanResponse empty = new LeadScanResponse();

        try {
            if (image == null || image.isEmpty()) {
                empty.setConfidence(0);
                empty.setNote("Không nhận được ảnh để quét.");
                return empty;
            }

            List<String> keys = getGeminiKeys();

            if (keys.isEmpty()) {
                empty.setConfidence(0);
                empty.setNote("Thiếu GEMINI_API_KEYS hoặc GEMINI_API_KEY trên Render.");
                return empty;
            }

            String imageHash = hashImage(image.getBytes());

            if (cache.containsKey(imageHash)) {
                LeadScanResponse cached = cache.get(imageHash);
                cached.setNote("Dữ liệu lấy từ cache, không gọi lại Gemini.");
                return cached;
            }

            LeadScanResponse result = extractLeadWithGeminiFailover(image, keys);

            normalizeLead(result);
            result.setConfidence(calculateConfidence(result));

            if (isBlank(result.getNote())) {
                result.setNote(result.getConfidence() >= 70
                        ? "Đã nhận diện xong. Vẫn nên kiểm tra lại trước khi lưu."
                        : "Đã quét xong nhưng dữ liệu còn thiếu, nên kiểm tra lại ảnh hoặc chụp rõ hơn.");
            }

            cache.put(imageHash, result);
            return result;

        } catch (Exception e) {
            empty.setConfidence(0);
            empty.setNote("OCR lỗi: " + safeError(e.getMessage()));
            return empty;
        }
    }

    private List<String> getGeminiKeys() {
        List<String> keys = new ArrayList<>();

        if (geminiApiKeys != null && !geminiApiKeys.isBlank()) {
            keys.addAll(Arrays.stream(geminiApiKeys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(this::looksLikeGeminiKey)
                    .toList());
        }

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            String singleKey = geminiApiKey.trim();

            if (looksLikeGeminiKey(singleKey) && !keys.contains(singleKey)) {
                keys.add(singleKey);
            }
        }

        return keys;
    }

    private boolean looksLikeGeminiKey(String key) {
    if (key == null) {
        return false;
    }

    key = key.trim();

    return !key.isEmpty();
}

    private LeadScanResponse extractLeadWithGeminiFailover(MultipartFile image, List<String> keys) throws Exception {
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < keys.size(); i++) {
            try {
                System.out.println("[LeadScanner] Đang thử Gemini key #" + (i + 1));
                LeadScanResponse response = callGeminiForLead(image, keys.get(i));
                response.setNote("Đã quét bằng Gemini key #" + (i + 1) + ".");
                return response;

            } catch (Exception e) {
                String message = safeError(e.getMessage());
                errors.add("Key #" + (i + 1) + ": " + message);
                System.out.println("[LeadScanner] Gemini key #" + (i + 1) + " lỗi: " + message);
            }
        }

        throw new RuntimeException("Toàn bộ Gemini API key đều lỗi hoặc hết quota. " + String.join(" | ", errors));
    }

    private LeadScanResponse callGeminiForLead(MultipartFile image, String apiKey) throws Exception {
        String mimeType = image.getContentType();

        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/jpeg";
        }

        String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

        String prompt = """
                Bạn là hệ thống trích xuất dữ liệu từ danh thiếp tại Việt Nam.

                Hãy đọc chữ trong ảnh và trả về đúng JSON sau:
                {
                  "companyName": "",
                  "contactName": "",
                  "position": "",
                  "phone": "",
                  "phone2": "",
                  "phone3": "",
                  "fax": "",
                  "email": "",
                  "website": "",
                  "taxCode": "",
                  "address": "",
                  "rawText": ""
                }

                Quy tắc:
                - Chỉ trả JSON hợp lệ, không markdown, không giải thích.
                - companyName là tên công ty, không lấy slogan.
                - contactName là tên người liên hệ, không lấy tên công ty.
                - phone, phone2, phone3 là số điện thoại, không lấy số fax.
                - fax chỉ lấy nếu dòng ghi Fax.
                - address lấy địa chỉ đầy đủ nhất.
                - rawText là toàn bộ chữ đọc được, giữ xuống dòng bằng \\n.
                - Nếu không thấy dữ liệu thì để chuỗi rỗng.
                """;

        var rootNode = objectMapper.createObjectNode();

        rootNode.set("contents", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .set("parts", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode().put("text", prompt))
                                .add(objectMapper.createObjectNode()
                                        .set("inline_data", objectMapper.createObjectNode()
                                                .put("mime_type", mimeType)
                                                .put("data", base64Image)
                                        )
                                )
                        )
                )
        );

        rootNode.set("generationConfig", objectMapper.createObjectNode()
                .put("temperature", 0)
                .put("response_mime_type", "application/json")
        );

        String requestBody = objectMapper.writeValueAsString(rootNode);

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel.trim()
                + ":generateContent?key="
                + apiKey.trim();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> geminiResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (geminiResponse.statusCode() < 200 || geminiResponse.statusCode() >= 300) {
            throw new RuntimeException("Gemini API trả lỗi " + geminiResponse.statusCode() + ": " + geminiResponse.body());
        }

        JsonNode root = objectMapper.readTree(geminiResponse.body());
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");

        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new RuntimeException("Gemini không trả về nội dung.");
        }

        String aiText = cleanJsonText(textNode.asText());

        try {
            JsonNode json = objectMapper.readTree(aiText);
            LeadScanResponse response = new LeadScanResponse();

            response.setCompanyName(asText(json, "companyName"));
            response.setContactName(asText(json, "contactName"));
            response.setPosition(asText(json, "position"));

            String phone = asText(json, "phone");
            String phone2 = asText(json, "phone2");
            String phone3 = asText(json, "phone3");
            response.setPhone(cleanPhoneList(joinPhones(phone, phone2, phone3)));

            response.setFax(cleanPhoneList(asText(json, "fax")));
            response.setEmail(asText(json, "email"));
            response.setWebsite(cleanWebsite(asText(json, "website")));
            response.setTaxCode(asText(json, "taxCode"));
            response.setAddress(asText(json, "address"));
            response.setRawText(asText(json, "rawText"));

            if (isAlmostEmpty(response) && !isBlank(response.getRawText())) {
                return parseRawText(response.getRawText());
            }

            return response;

        } catch (Exception jsonError) {
            return parseRawText(aiText);
        }
    }

    private boolean isAlmostEmpty(LeadScanResponse response) {
        return isBlank(response.getCompanyName())
                && isBlank(response.getContactName())
                && isBlank(response.getPhone())
                && isBlank(response.getEmail())
                && isBlank(response.getWebsite())
                && isBlank(response.getAddress());
    }

    private String joinPhones(String phone, String phone2, String phone3) {
        List<String> list = new ArrayList<>();

        if (!isBlank(phone)) list.add(phone);
        if (!isBlank(phone2)) list.add(phone2);
        if (!isBlank(phone3)) list.add(phone3);

        return String.join(", ", list);
    }

    private String cleanJsonText(String text) {
        if (text == null) return "";

        String cleaned = text.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        }

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        return cleaned.trim();
    }

    private String asText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private void normalizeLead(LeadScanResponse response) {
        response.setCompanyName(cleanValue(response.getCompanyName()));
        response.setContactName(cleanContactName(response.getContactName(), response.getCompanyName()));
        response.setPosition(cleanValue(response.getPosition()));
        response.setPhone(cleanPhoneList(response.getPhone()));
        response.setFax(cleanPhoneList(response.getFax()));
        response.setEmail(cleanValue(response.getEmail()));
        response.setWebsite(cleanWebsite(response.getWebsite()));
        response.setTaxCode(cleanValue(response.getTaxCode()));
        response.setAddress(cleanAddress(response.getAddress()));
        response.setRawText(response.getRawText() == null ? "" : response.getRawText().trim());
    }

    private String cleanValue(String value) {
        if (value == null) return "";

        return value.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("^[：:|\\-\\s]+", "")
                .replaceAll("[：:|\\-\\s]+$", "");
    }

    private String cleanContactName(String contactName, String companyName) {
        String value = cleanValue(contactName);

        if (value.isBlank()) return "";

        value = value.replaceAll("(?i)^(Mr|Ms|Mrs|Miss|Ông|Bà|Anh|Chị)\\.?\\s*", "").trim();

        if (!isBlank(companyName) && normalize(value).equals(normalize(companyName))) {
            return "";
        }

        return value;
    }

    private String cleanPhoneList(String phone) {
        if (phone == null || phone.isBlank()) return "";

        Matcher matcher = PHONE_PATTERN.matcher(phone);
        List<String> phones = new ArrayList<>();

        while (matcher.find()) {
            String number = matcher.group().replaceAll("[^0-9+]", "");

            if (!phones.contains(number)) {
                phones.add(number);
            }
        }

        return phones.isEmpty() ? phone.trim() : String.join(", ", phones);
    }

    private String cleanWebsite(String website) {
        String value = cleanValue(website);

        if (value.isBlank()) return "";

        String lower = value.toLowerCase();

        if (lower.contains("@")) return "";

        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            value = "https://" + value;
        }

        return value;
    }

    private String cleanAddress(String address) {
        String value = cleanValue(address);
        value = value.replaceAll("(?i)^address\\s*[:：]?\\s*", "");
        value = value.replaceAll("(?i)^địa chỉ\\s*[:：]?\\s*", "");
        value = value.replaceAll("\\s*,\\s*", ", ");
        return value.trim();
    }

    public LeadScanResponse parseRawText(String rawText) {
        LeadScanResponse response = new LeadScanResponse();
        String text = rawText == null ? "" : rawText.trim();
        response.setRawText(text);

        if (text.isBlank()) {
            response.setConfidence(0);
            response.setNote("Không có dữ liệu OCR để phân tích.");
            return response;
        }

        List<String> lines = cleanLines(text);

        response.setPhone(findPhone(text));
        response.setFax(findFax(text));
        response.setEmail(findEmail(text));
        response.setWebsite(findWebsite(text));
        response.setTaxCode(findTaxCode(text));
        response.setCompanyName(findCompanyName(lines));
        response.setContactName(findContactName(lines, response.getCompanyName()));
        response.setPosition(findPosition(lines));
        response.setAddress(findAddress(lines));
        response.setConfidence(calculateConfidence(response));
        response.setNote(response.getConfidence() >= 70
                ? "Đã nhận diện xong. Vẫn nên kiểm tra lại trước khi lưu."
                : "Một số trường còn thiếu hoặc chưa chắc chắn, nên kiểm tra thủ công.");

        normalizeLead(response);
        return response;
    }

    private List<String> cleanLines(String text) {
        String[] split = text.split("\\r?\\n");
        List<String> lines = new ArrayList<>();

        for (String line : split) {
            String cleaned = line.trim().replaceAll("\\s+", " ");

            if (cleaned.isBlank()) continue;

            String normalized = normalize(cleaned);

            if (normalized.contains("at your side")
                    || normalized.contains("your partner")
                    || normalized.contains("since")) {
                continue;
            }

            lines.add(cleaned);
        }

        return lines;
    }

    private String findPhone(String text) {
        String[] lines = text.split("\\r?\\n");
        List<String> phones = new ArrayList<>();

        for (String line : lines) {
            String normalized = normalize(line);

            if (normalized.contains("fax")) continue;

            Matcher matcher = PHONE_PATTERN.matcher(line);

            while (matcher.find()) {
                String number = matcher.group().replaceAll("[^0-9+]", "");

                if (!phones.contains(number)) {
                    phones.add(number);
                }
            }
        }

        return String.join(", ", phones);
    }

    private String findFax(String text) {
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            if (normalize(line).contains("fax")) {
                Matcher matcher = PHONE_PATTERN.matcher(line);

                if (matcher.find()) {
                    return matcher.group().replaceAll("[^0-9+]", "");
                }
            }
        }

        return "";
    }

    private String findEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private String findWebsite(String text) {
        Matcher matcher = WEBSITE_PATTERN.matcher(text);

        while (matcher.find()) {
            String value = matcher.group();
            String lower = value.toLowerCase();

            if (!lower.contains("@") && !lower.matches(".*\\.(jpg|png|pdf|doc|docx|xls|xlsx)$")) {
                if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                    return "https://" + value;
                }

                return value;
            }
        }

        return "";
    }

    private String findTaxCode(String text) {
        Matcher matcher = TAX_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(2).replaceAll("[^0-9-]", "") : "";
    }

    private String findCompanyName(List<String> lines) {
        for (String line : lines) {
            String normalized = normalize(line);

            if (normalized.contains("cong ty")
                    || normalized.contains("tnhh")
                    || normalized.contains("jsc")
                    || normalized.contains("co., ltd")
                    || normalized.contains("company")
                    || normalized.contains("enterprise")
                    || normalized.contains("corporation")
                    || normalized.contains("corp")) {
                return line;
            }
        }

        for (String line : lines) {
            String normalized = normalize(line);

            if (!isLikelyContactLine(normalized)
                    && !isInfoLine(normalized)
                    && line.length() >= 3) {
                return line;
            }
        }

        return "";
    }

    private String findContactName(List<String> lines, String companyName) {
        for (String line : lines) {
            String normalized = normalize(line);

            if (!isBlank(companyName) && normalize(line).equals(normalize(companyName))) continue;

            if (normalized.matches(".*\\b(mr|ms|mrs|miss|ong|ba|anh|chi)\\b.*")) {
                return line.replaceAll("(?i)^(Mr|Ms|Mrs|Miss|Ông|Bà|Anh|Chị)\\.?\\s*", "").trim();
            }
        }

        for (String line : lines) {
            String normalized = normalize(line);

            if (!isBlank(companyName) && normalize(line).equals(normalize(companyName))) continue;

            if (isLikelyVietnameseName(line) && !isInfoLine(normalized)) {
                return line;
            }
        }

        return "";
    }

    private String findPosition(List<String> lines) {
        for (String line : lines) {
            String normalized = normalize(line);

            if (normalized.contains("manager")
                    || normalized.contains("director")
                    || normalized.contains("sales")
                    || normalized.contains("marketing")
                    || normalized.contains("giam doc")
                    || normalized.contains("truong phong")
                    || normalized.contains("nhan vien")
                    || normalized.contains("ke toan")
                    || normalized.contains("assistant")
                    || normalized.contains("engineer")) {
                return line;
            }
        }

        return "";
    }

    private String findAddress(List<String> lines) {
        List<String> addressLines = new ArrayList<>();

        for (String line : lines) {
            String normalized = normalize(line);

            if (normalized.contains("duong")
                    || normalized.contains("phuong")
                    || normalized.contains("quan")
                    || normalized.contains("tp")
                    || normalized.contains("thanh pho")
                    || normalized.contains("tinh")
                    || normalized.contains("kcn")
                    || normalized.contains("lo ")
                    || normalized.contains("address")
                    || normalized.contains("dia chi")
                    || normalized.contains("street")
                    || normalized.contains("district")
                    || normalized.contains("ward")) {
                addressLines.add(line.replaceAll("(?i)^(Địa chỉ|Dia chi|Address)\\s*[:：]?\\s*", "").trim());
            }
        }

        return String.join(", ", addressLines);
    }

    private boolean isLikelyContactLine(String normalized) {
        return normalized.contains("mr ")
                || normalized.contains("ms ")
                || normalized.contains("mrs ")
                || normalized.contains("ong ")
                || normalized.contains("ba ")
                || normalized.contains("anh ")
                || normalized.contains("chi ");
    }

    private boolean isInfoLine(String normalized) {
        return normalized.contains("@")
                || normalized.contains("www.")
                || normalized.contains("http")
                || normalized.contains("tel")
                || normalized.contains("phone")
                || normalized.contains("mobile")
                || normalized.contains("hotline")
                || normalized.contains("fax")
                || normalized.contains("tax")
                || normalized.contains("mst")
                || normalized.contains("dia chi")
                || normalized.contains("address")
                || normalized.matches(".*\\d{4,}.*");
    }

    private boolean isLikelyVietnameseName(String line) {
        String cleaned = line.trim();

        if (cleaned.length() < 5 || cleaned.length() > 40) return false;
        if (cleaned.matches(".*\\d.*")) return false;

        String[] words = cleaned.split("\\s+");

        if (words.length < 2 || words.length > 5) return false;

        int uppercaseStart = 0;

        for (String word : words) {
            if (!word.isBlank() && Character.isUpperCase(word.charAt(0))) {
                uppercaseStart++;
            }
        }

        return uppercaseStart >= 2;
    }

    private Integer calculateConfidence(LeadScanResponse response) {
        int score = 20;

        if (!isBlank(response.getCompanyName())) score += 15;
        if (!isBlank(response.getContactName())) score += 10;
        if (!isBlank(response.getPhone())) score += 20;
        if (!isBlank(response.getEmail())) score += 15;
        if (!isBlank(response.getWebsite())) score += 10;
        if (!isBlank(response.getAddress())) score += 15;
        if (!isBlank(response.getTaxCode())) score += 5;

        return Math.min(score, 100);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String normalize(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase();
    }

    private String hashImage(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encoded = digest.digest(bytes);

        StringBuilder hex = new StringBuilder();

        for (byte b : encoded) {
            hex.append(String.format("%02x", b));
        }

        return hex.toString();
    }

    private String safeError(String message) {
        if (message == null) return "";
        return message.replaceAll("AIza[0-9A-Za-z_\\-]+", "AIza***");
    }
}