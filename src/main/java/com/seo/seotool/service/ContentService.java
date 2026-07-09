package com.seo.seotool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seo.seotool.dto.ContentGenerateRequest;
import com.seo.seotool.dto.ContentGenerateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class ContentService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    public ContentGenerateResponse generateContent(ContentGenerateRequest request) throws Exception {
        validateRequest(request);

        if (isBlank(geminiApiKey)) {
            return ContentGenerateResponse.error(
                    "Thiếu GEMINI_API_KEY. Hãy thêm biến môi trường GEMINI_API_KEY trên Render hoặc application.properties."
            );
        }

        String prompt = buildSeoPrompt(request);
        String aiContent = callGemini(prompt);

        String title = extractSection(aiContent, "TITLE SEO:", "META DESCRIPTION:");
        String metaDescription = extractSection(aiContent, "META DESCRIPTION:", "OUTLINE:");
        String outline = extractSection(aiContent, "OUTLINE:", "BÀI VIẾT:");
        String content = aiContent.trim();

        return ContentGenerateResponse.success(
                cleanText(title),
                cleanText(metaDescription),
                cleanText(outline),
                content
        );
    }

    private void validateRequest(ContentGenerateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request không được để trống.");
        }

        if (isBlank(request.getTopic())) {
            throw new IllegalArgumentException("Chủ đề bài viết không được để trống.");
        }

        if (isBlank(request.getMainKeyword())) {
            throw new IllegalArgumentException("Từ khóa chính không được để trống.");
        }
    }

    private String callGemini(String prompt) throws Exception {
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel
                + ":generateContent?key="
                + geminiApiKey;

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(180000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        String body = buildGeminiRequestBody(prompt);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        String responseText;

        if (status >= 200 && status < 300) {
            responseText = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } else {
            responseText = new String(connection.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException("Gemini API lỗi HTTP " + status + ": " + responseText);
        }

        JsonNode root = objectMapper.readTree(responseText);

        JsonNode textNode = root
                .path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text");

        if (textNode.isMissingNode() || isBlank(textNode.asText())) {
            throw new RuntimeException("Gemini không trả về nội dung hợp lệ.");
        }

        return textNode.asText();
    }

    private String buildGeminiRequestBody(String prompt) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = objectMapper.createArrayNode();

        ObjectNode part = objectMapper.createObjectNode();
        part.put("text", prompt);

        parts.add(part);
        content.put("role", "user");
        content.set("parts", parts);
        contents.add(content);

        ObjectNode generationConfig = objectMapper.createObjectNode();
        generationConfig.put("temperature", 0.45);
        generationConfig.put("topP", 0.85);
        generationConfig.put("topK", 40);
        generationConfig.put("maxOutputTokens", 12000);

        root.set("contents", contents);
        root.set("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(root);
    }

    private String buildSeoPrompt(ContentGenerateRequest request) {
        String topic = safe(request.getTopic());
        String mainKeyword = safe(request.getMainKeyword());
        String subKeywords = safe(request.getSubKeywords());
        String tone = safeDefault(request.getTone(), "Chuyên nghiệp");
        String length = safeDefault(request.getLength(), "Trung bình 1000 từ");
        String note = safe(request.getNote());

        return """
                Bạn là chuyên gia SEO Content tiếng Việt cho website doanh nghiệp B2B.
                Hãy viết một bài content chuẩn SEO tiếng Việt theo bố cục giống mẫu bài MECI.

                CHỦ ĐỀ:
                %s

                TỪ KHÓA CHÍNH:
                %s

                TỪ KHÓA PHỤ:
                %s

                GIỌNG VĂN:
                %s

                ĐỘ DÀI MONG MUỐN:
                %s

                GHI CHÚ THÊM:
                %s

                PHONG CÁCH VIẾT:
                - Viết cho doanh nghiệp, nhà xưởng, kho hàng, khu sản xuất.
                - Giọng văn chuyên nghiệp, rõ ràng, thực tế.
                - Khách hàng đã biết vấn đề họ gặp phải, không được viết theo kiểu xem thường người đọc.
                - Không lan man, không viết sáo rỗng, không nhồi keyword.
                - Không viết kiểu giáo trình hoặc giải thích quá rộng.
                - Nội dung cần khơi gợi đúng vấn đề và đưa ra hướng kiểm tra, xử lý phù hợp.
                - Không tự bịa số liệu, chứng nhận, khách hàng, thương hiệu hoặc cam kết nếu người dùng không cung cấp.
                - Nếu nhắc MECI, chỉ viết theo hướng cung cấp giải pháp kiểm tra, tư vấn, sửa chữa, thay thế hoặc lắp đặt phù hợp.

                ĐỘ DÀI VÀ CÁCH TRIỂN KHAI:
                - Tổng bài viết khoảng 1500 đến 2200 từ.
                - Ưu tiên hoàn thành đủ 6 mục hơn là viết quá dài ở các mục đầu.
                - Mỗi mục lớn chỉ viết 2 đến 4 đoạn ngắn.
                - Mục 2 và mục 3 chỉ liệt kê tối đa 5 đầu mục.
                - Mỗi đầu mục trong mục 2 và mục 3 chỉ viết 2 đến 3 câu.
                - Không tách thêm gạch đầu dòng con bên trong từng đầu mục.
                - Không viết lặp ý giữa các mục.
                - Không dùng cụm "Tên lỗi" trước mỗi đầu mục.
                - Viết thẳng theo dạng: "Ray bị móp méo: nội dung".

                FORMAT TRẢ VỀ BẮT BUỘC:

                TITLE SEO:
                [Viết title SEO dưới 60 ký tự nếu có thể]

                META DESCRIPTION:
                [Viết meta description khoảng 140 đến 160 ký tự]

                OUTLINE:
                1. [Tên mục 1]
                2. [Tên mục 2]
                3. [Tên mục 3]
                4. [Tên mục 4]
                5. MECI cung cấp giải pháp [phù hợp với chủ đề]
                6. Câu hỏi thường gặp (FAQ)

                BÀI VIẾT:
                [Viết lại tiêu đề bài viết]

                [Đoạn mở bài 2 đến 4 câu. Mở bài phải nêu đúng vấn đề, bối cảnh sử dụng và dẫn vào nội dung chính.]

                1. [Tên mục 1]
                [Viết 2 đến 4 đoạn ngắn. Nội dung phải đúng trọng tâm, không lan man.]

                2. [Tên mục 2]
                [Viết đoạn dẫn ngắn 2 đến 3 câu.]

                [Tên lỗi/ý chính 1]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 2]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 3]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 4]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 5]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]

                [Viết 1 đoạn ngắn chốt lại mục 2.]

                3. [Tên mục 3]
                [Viết đoạn dẫn ngắn 2 đến 3 câu.]

                [Tên lỗi/ý chính 1]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 2]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 3]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 4]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]
                [Tên lỗi/ý chính 5]: [Giải thích ngắn gọn trong 2 đến 3 câu, không tách thêm gạch đầu dòng con.]

                [Viết 1 đoạn ngắn chốt lại mục 3.]

                4. [Tên mục 4]
                [Viết phần hướng dẫn kiểm tra/xử lý theo từng nhóm rõ ràng, ngắn gọn.]

                Với [nhóm 1], cần kiểm tra:
                - [Hạng mục kiểm tra 1]
                - [Hạng mục kiểm tra 2]
                - [Hạng mục kiểm tra 3]
                - [Hạng mục kiểm tra 4]
                - [Hạng mục kiểm tra 5]

                Với [nhóm 2], cần kiểm tra:
                - [Hạng mục kiểm tra 1]
                - [Hạng mục kiểm tra 2]
                - [Hạng mục kiểm tra 3]
                - [Hạng mục kiểm tra 4]
                - [Hạng mục kiểm tra 5]

                [Viết đoạn ngắn tư vấn khi nào nên sửa, khi nào nên thay mới nếu phù hợp.]

                5. MECI cung cấp giải pháp [phù hợp với chủ đề]
                [Viết 3 đến 5 đoạn ngắn giới thiệu MECI cung cấp giải pháp kiểm tra, sửa chữa, thay thế hoặc lắp đặt. Nội dung phải tự nhiên, không quảng cáo quá đà.]

                [Viết đoạn chốt lợi ích: xử lý đúng lỗi, giảm gián đoạn vận hành, tối ưu chi phí bảo trì.]

                6. Câu hỏi thường gặp (FAQ)

                6.1. [Câu hỏi 1]
                [Trả lời 3 đến 5 câu, ngắn gọn.]

                6.2. [Câu hỏi 2]
                [Trả lời 3 đến 5 câu, ngắn gọn.]

                6.3. [Câu hỏi 3]
                [Trả lời 3 đến 5 câu, ngắn gọn.]

                LƯU Ý BẮT BUỘC:
                - Không dùng markdown kiểu **in đậm**.
                - Không dùng ký hiệu # cho heading.
                - Heading phải đánh số giống mẫu: 1., 2., 3., 4., 5., 6.
                - FAQ phải đánh số 6.1, 6.2, 6.3.
                - Các lỗi hoặc ý chính trong mục 2 và 3 phải viết dạng: "Tên lỗi: nội dung".
                - Không viết "Tên lỗi Ray..." hoặc "Tên lỗi Trục...".
                - Không viết quá ngắn, nhưng tuyệt đối không lan man.
                - Không tự thêm hình ảnh.
                - Không dừng bài ở mục 5, phải viết đủ đến hết FAQ.
                """.formatted(topic, mainKeyword, subKeywords, tone, length, note);
    }

    private String extractSection(String text, String startMarker, String endMarker) {
        if (isBlank(text)) {
            return "";
        }

        int start = text.indexOf(startMarker);

        if (start < 0) {
            return "";
        }

        start += startMarker.length();

        int end = text.indexOf(endMarker, start);

        if (end < 0) {
            return text.substring(start).trim();
        }

        return text.substring(start, end).trim();
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private String safeDefault(String value, String defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }

        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}