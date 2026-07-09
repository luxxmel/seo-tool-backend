package com.seo.seotool.controller;

import com.seo.seotool.dto.LeadScanRequest;
import com.seo.seotool.dto.LeadScanResponse;
import com.seo.seotool.service.LeadScannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/lead-scanner")
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
public class LeadScannerController {

    @Autowired
    private LeadScannerService leadScannerService;

    @GetMapping("/health")
    public String health() {
        return "Lead Scanner API is running";
    }

    @PostMapping("/parse")
    public LeadScanResponse parseLead(@RequestBody LeadScanRequest request) {
        return leadScannerService.parseRawText(request.getRawText());
    }

    @PostMapping("/scan")
    public LeadScanResponse scanLead(@RequestParam("image") MultipartFile image) {
        return leadScannerService.scanImage(image);
    }
}