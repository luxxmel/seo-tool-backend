package com.seo.seotool.service;

import com.seo.seotool.dto.LeadScanResponse;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LeadScannerService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+84|0)([\\s.\\-()]?\\d){8,12}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern WEBSITE_PATTERN = Pattern.compile("(?i)(https?://)?(www\\.)?[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]*)?");
    private static final Pattern TAX_PATTERN = Pattern.compile("(?i)(MST|Ma so thue|Mã số thuế|Tax code)[:\\s]*([0-9\\-]{10,15})");

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
        response.setEmail(findEmail(text));
        response.setWebsite(findWebsite(text));
        response.setTaxCode(findTaxCode(text));
        response.setCompanyName(findCompanyName(lines));
        response.setContactName(findContactName(lines, response.getCompanyName()));
        response.setPosition(findPosition(lines));
        response.setAddress(findAddress(lines));
        response.setConfidence(calculateConfidence(response));
        response.setNote(response.getConfidence() >= 80 ? "Dữ liệu khá ổn, vẫn nên kiểm tra lại trước khi lưu." : "Một số trường còn thiếu hoặc chưa chắc chắn, nên kiểm tra thủ công.");

        return response;
    }

    private List<String> cleanLines(String text) {
        String[] split = text.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String line : split) {
            String cleaned = line.trim().replaceAll("\\s+", " ");
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private String findPhone(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().replaceAll("[^0-9+]", "");
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
            if (normalized.contains("cong ty") || normalized.contains("tnhh") || normalized.contains("jsc") || normalized.contains("co., ltd") || normalized.contains("company") || normalized.contains("enterprise")) {
                return line;
            }
        }
        return lines.isEmpty() ? "" : lines.get(0);
    }

    private String findContactName(List<String> lines, String companyName) {
        for (String line : lines) {
            String normalized = normalize(line);
            if (line.equalsIgnoreCase(companyName)) continue;
            if (normalized.contains("mr ") || normalized.contains("ms ") || normalized.contains("mrs ") || normalized.contains("ong ") || normalized.contains("ba ")) {
                return line.replaceAll("(?i)^(Mr|Ms|Mrs|Ông|Bà)\\.?\\s*", "").trim();
            }
        }
        return "";
    }

    private String findPosition(List<String> lines) {
        for (String line : lines) {
            String normalized = normalize(line);
            if (normalized.contains("manager") || normalized.contains("director") || normalized.contains("sales") || normalized.contains("marketing") || normalized.contains("giam doc") || normalized.contains("truong phong") || normalized.contains("nhan vien")) {
                return line;
            }
        }
        return "";
    }

    private String findAddress(List<String> lines) {
        List<String> addressLines = new ArrayList<>();
        for (String line : lines) {
            String normalized = normalize(line);
            if (normalized.contains("duong") || normalized.contains("phuong") || normalized.contains("quan") || normalized.contains("tp") || normalized.contains("tinh") || normalized.contains("kcn") || normalized.contains("lo ") || normalized.contains("address") || normalized.contains("dia chi")) {
                addressLines.add(line.replaceAll("(?i)^Địa chỉ:?\\s*", "").trim());
            }
        }
        return String.join(", ", addressLines);
    }

    private Integer calculateConfidence(LeadScanResponse response) {
        int score = 20;
        if (!isBlank(response.getCompanyName())) score += 15;
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
}
