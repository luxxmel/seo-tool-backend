package com.seo.seotool.controller;

import com.seo.seotool.dto.UrlRequest;
import com.seo.seotool.service.SeoService;
import jakarta.servlet.http.HttpServletRequest;

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
                "https://indextool.netlify.app"
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

    @PostMapping("/direct-ping")
    public List<Map<String, Object>> directPing(@RequestBody UrlRequest request) {
        return seoService.processDirectPing(request.getUrls());
    }

    @PostMapping("/add-url")
    public Map<String, Object> addUrl(@RequestBody UrlRequest request) {
        return seoService.addUrlsToHub(request.getUrls());
    }

    @PostMapping("/audit")
    public List<Map<String, Object>> auditLinks(@RequestBody UrlRequest request) {
        return seoService.processAudit(request.getUrls());
    }

    @PostMapping("/ping-due")
    public Map<String, Object> pingDue() {
        return seoService.pingDueUrls();
    }

    @GetMapping("/url-status")
    public List<Map<String, Object>> urlStatus() {
        return seoService.getUrlStatus();
    }

    @GetMapping("/bot-logs")
    public List<Map<String, Object>> botLogs() {
        return seoService.getBotLogs();
    }

    @GetMapping(value = "/discovery", produces = MediaType.TEXT_HTML_VALUE)
    public String discoveryLanding(HttpServletRequest request) {
        seoService.trackBotVisit(request);
        return seoService.renderDiscoveryLandingHtml();
    }

    @GetMapping(value = "/discovery/page/{page}", produces = MediaType.TEXT_HTML_VALUE)
    public String discoveryPage(@PathVariable int page, HttpServletRequest request) {
        seoService.trackBotVisit(request);
        return seoService.renderDiscoveryPageHtml(page);
    }

    @GetMapping(value = "/discovery-sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String discoverySitemap(HttpServletRequest request) {
        seoService.trackBotVisit(request);
        return seoService.renderDiscoverySitemapXml();
    }

    @GetMapping(value = "/discovery-rss.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String discoveryRss(HttpServletRequest request) {
        seoService.trackBotVisit(request);
        return seoService.renderDiscoveryRssXml();
    }

    @GetMapping("/discovery-summary")
    public Map<String, Object> discoverySummary() {
        return seoService.getDiscoverySummary();
    }

    @GetMapping(value = "/index-hub", produces = MediaType.TEXT_HTML_VALUE)
    public String indexHub(HttpServletRequest request) {
        seoService.trackBotVisit(request);
        return seoService.renderIndexHubHtml();
    }

    @GetMapping(value = "/index-hub/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String indexHubSitemap(HttpServletRequest request) {
        seoService.trackBotVisit(request);
        return seoService.renderIndexHubSitemapXml();
    }

    @GetMapping(value = "/index-hub/rss.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String indexHubRss(HttpServletRequest request) {
        seoService.trackBotVisit(request);
        return seoService.renderIndexHubRssXml();
    }
}
