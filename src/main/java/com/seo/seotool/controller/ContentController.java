package com.seo.seotool.controller;

import com.seo.seotool.dto.ContentGenerateRequest;
import com.seo.seotool.dto.ContentGenerateResponse;
import com.seo.seotool.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:3000",
                "https://index-tool.netlify.app"
        },
        methods = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.OPTIONS
        },
        allowedHeaders = "*"
)
public class ContentController {

    @Autowired
    private ContentService contentService;

    @GetMapping("/health")
    public String health() {
        return "Content API OK";
    }

    @PostMapping("/generate")
    public ContentGenerateResponse generateContent(
            @RequestBody ContentGenerateRequest request
    ) {
        try {
            return contentService.generateContent(request);
        } catch (Exception e) {
            return ContentGenerateResponse.error(
                    "Generate content lỗi: " + e.getMessage()
            );
        }
    }
}