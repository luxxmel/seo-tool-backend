package com.seo.seotool.controller;

import com.seo.seotool.dto.AuditRequest;
import com.seo.seotool.dto.AuditResponse;
import com.seo.seotool.service.WebsiteAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
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
public class AuditController {

    @Autowired
    private WebsiteAuditService websiteAuditService;

    @GetMapping("/health")
    public String health() {
        return "Audit API OK";
    }

    @PostMapping("/scan")
    public AuditResponse scanWebsite(@RequestBody AuditRequest request) {
        return websiteAuditService.scanWebsite(request);
    }
}