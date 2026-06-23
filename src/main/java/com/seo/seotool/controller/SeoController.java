package com.seo.seotool.controller;

import com.seo.seotool.dto.UrlRequest;
import com.seo.seotool.service.SeoService;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seo")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "https://index-tool.netlify.app"
})
public class SeoController {

    @Autowired
    private SeoService seoService;

    @GetMapping("/")
    public String healthCheck() {
        return "SEO Tool Backend is running";
    }

    @PostMapping("/ping")
    public String pingIndexing(@RequestBody UrlRequest request) {
        return seoService.processPing(request.getUrls());
    }

    @PostMapping("/audit")
    public List<Map<String, Object>> auditLinks(@RequestBody UrlRequest request) {
        return seoService.processAudit(request.getUrls());
    }

    @PostMapping("/direct-ping")
    public List<Map<String, Object>> directPing(@RequestBody UrlRequest request) {
        return seoService.processDirectPing(request.getUrls());
    }

    @PostMapping("/check-index")
    public List<Map<String, Object>> checkIndex(@RequestBody UrlRequest request) {
        return seoService.processCheckIndex(request.getUrls());
    }
}