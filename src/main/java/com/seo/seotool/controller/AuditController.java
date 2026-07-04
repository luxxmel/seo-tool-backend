package com.seo.seotool.controller;

import com.seo.seotool.dto.CheckIndexRequest;
import com.seo.seotool.dto.CheckIndexResponse;
import com.seo.seotool.service.WebsiteAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
public class AuditController {

    @Autowired
    private WebsiteAuditService websiteAuditService;

    @GetMapping("/health")
    public String health() {
        return "Check Index API OK";
    }

    @PostMapping("/check-index")
    public List<CheckIndexResponse> checkIndex(@RequestBody CheckIndexRequest request) {
        return websiteAuditService.checkIndexBySerper(request);
    }
}