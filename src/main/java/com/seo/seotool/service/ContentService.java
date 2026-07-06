package com.seo.seotool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String OPENAI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    public ContentGenerateResponse generateContent(ContentGenerateRequest request) throws Exception {
        validateRequest(request);

        if (isBlank(openAiApiKey)) {
            return ContentGenerateResponse.error(
                    "Thiếu OPENAI_API_KEY. Hãy thêm biến môi trường OPENAI_API_KEY trên Render hoặc application.properties."
            );
        }

        String prompt = buildSeoPrompt(request);
        String aiContent = callOpenAi(prompt);

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

    private String callOpenAi(String prompt) throws Exception {
        URL url = new URL(OPENAI_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);

        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + openAiApiKey);

        String body = buildOpenAiRequestBody(prompt);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        String responseText;

        if (status >= 200 && status < 300) {
            responseText = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } else {
            responseText = new String(connection.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException("OpenAI API lỗi HTTP " + status + ": " + responseText);
        }

        JsonNode root = objectMapper.readTree(responseText);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");

        if (contentNode.isMissingNode() || isBlank(contentNode.asText())) {
            throw new RuntimeException("OpenAI không trả về nội dung hợp lệ.");
        }

        return contentNode.asText();
    }

    private String buildOpenAiRequestBody(String prompt) throws Exception {
        String systemPrompt = """
                Bạn là chuyên gia SEO Content tiếng Việt cho website doanh nghiệp B2B.
                Bạn viết nội dung theo phong cách rõ ràng, thực tế, đúng trọng tâm.
                Không viết sáo rỗng, không phóng đại, không nhồi từ khóa.
                Không bịa số liệu, chứng nhận, khách hàng, thương hiệu hoặc cam kết nếu người dùng không cung cấp.
                Nếu nội dung liên quan đến MECI, hãy viết theo hướng tư vấn giải pháp nhà xưởng chuyên nghiệp.
                """;

        Object requestBody = objectMapper.createObjectNode()
                .put("model", openAiModel)
                .put("temperature", 0.65)
                .put("max_tokens", 4500)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", systemPrompt))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt))
                );

        return objectMapper.writeValueAsString(requestBody);
    }

    private String buildSeoPrompt(ContentGenerateRequest request) {
        String topic = safe(request.getTopic());
        String mainKeyword = safe(request.getMainKeyword());
        String subKeywords = safe(request.getSubKeywords());
        String tone = safeDefault(request.getTone(), "Chuyên nghiệp");
        String length = safeDefault(request.getLength(), "Trung bình 1000 từ");
        String note = safe(request.getNote());

        return """
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
                - Nội dung cần khơi gợi đúng vấn đề và đưa ra hướng kiểm tra, xử lý phù hợp.
                - Không tự bịa số liệu, chứng nhận, khách hàng, thương hiệu hoặc cam kết nếu người dùng không cung cấp.
                - Nếu nhắc MECI, chỉ viết theo hướng cung cấp giải pháp kiểm tra, tư vấn, sửa chữa, thay thế hoặc lắp đặt phù hợp.

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
                [Viết 2 đến 4 đoạn giải thích. Nội dung phải đúng trọng tâm.]

                2. [Tên mục 2]
                [Viết đoạn dẫn ngắn.]

                [Đầu mục 1]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 2]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 3]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 4]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 5]: [Nội dung giải thích sau dấu hai chấm.]

                [Viết 1 đoạn chốt lại mục 2.]

                3. [Tên mục 3]
                [Viết đoạn dẫn ngắn.]

                [Đầu mục 1]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 2]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 3]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 4]: [Nội dung giải thích sau dấu hai chấm.]
                [Đầu mục 5]: [Nội dung giải thích sau dấu hai chấm.]

                [Viết 1 đoạn chốt lại mục 3.]

                4. [Tên mục 4]
                [Viết phần hướng dẫn kiểm tra/xử lý theo từng nhóm rõ ràng.]

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

                [Viết đoạn tư vấn khi nào nên sửa, khi nào nên thay mới nếu phù hợp.]

                5. MECI cung cấp giải pháp [phù hợp với chủ đề]
                [Viết 3 đến 5 đoạn giới thiệu MECI cung cấp giải pháp kiểm tra, sửa chữa, thay thế hoặc lắp đặt. Nội dung phải tự nhiên, không quảng cáo quá đà.]

                [Viết đoạn chốt lợi ích: xử lý đúng lỗi, giảm gián đoạn vận hành, tối ưu chi phí bảo trì.]

                6. Câu hỏi thường gặp (FAQ)

                6.1. [Câu hỏi 1]
                [Trả lời 3 đến 5 câu.]

                6.2. [Câu hỏi 2]
                [Trả lời 3 đến 5 câu.]

                6.3. [Câu hỏi 3]
                [Trả lời 3 đến 5 câu.]

                LƯU Ý:
                - Không dùng markdown kiểu **in đậm**.
                - Không dùng ký hiệu # cho heading.
                - Heading phải đánh số giống mẫu: 1., 2., 3., 4., 5., 6.
                - FAQ phải đánh số 6.1, 6.2, 6.3.
                - Các lỗi hoặc ý chính trong mục 2 và 3 phải viết dạng: "Tên lỗi: nội dung".
                - Không viết quá ngắn.
                - Không tự thêm hình ảnh.
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