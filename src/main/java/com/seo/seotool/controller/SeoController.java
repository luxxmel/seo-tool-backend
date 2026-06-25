package com.seo.seotool.controller;

import com.seo.seotool.dto.UrlRequest;
import com.seo.seotool.service.SeoService;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seo")
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
public class SeoController {

    @Autowired
    private SeoService seoService;

    @GetMapping("/")
    public String healthCheck() {
        return "SEO Tool Backend is running";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /*
     * Fast Ping:
     * Ping thẳng URL, không check HTML/noindex/alive.
     * URL hợp lệ sẽ tự được thêm vào Index Hub.
     */
    @PostMapping("/direct-ping")
    public List<Map<String, Object>> directPing(@RequestBody UrlRequest request) {
        return seoService.processDirectPing(request.getUrls());
    }

    /*
     * Audit để riêng, khi nào cần kiểm tra sống/chết/noindex thì dùng.
     */
    @PostMapping("/audit")
    public List<Map<String, Object>> auditLinks(@RequestBody UrlRequest request) {
        return seoService.processAudit(request.getUrls());
    }

    /*
     * Check index để riêng.
     */
    @PostMapping("/check-index")
    public List<Map<String, Object>> checkIndex(@RequestBody UrlRequest request) {
        return seoService.processCheckIndex(request.getUrls());
    }

    /*
     * Public Index Hub cho Google bot crawl.
     */
    @GetMapping(value = "/index-hub", produces = MediaType.TEXT_HTML_VALUE)
    public String indexHub() {
        return seoService.renderIndexHubHtml();
    }

    /*
     * Sitemap riêng cho các URL đã submit vào Hub.
     */
    @GetMapping(value = "/index-hub/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String indexHubSitemap() {
        return seoService.renderIndexHubSitemapXml();
    }

    /*
     * RSS feed cho tầng phát hiện URL.
     */
    @GetMapping(value = "/index-hub/rss.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String indexHubRss() {
        return seoService.renderIndexHubRssXml();
    }
}