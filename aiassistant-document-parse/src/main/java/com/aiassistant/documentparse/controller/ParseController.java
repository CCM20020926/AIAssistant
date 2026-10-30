package com.aiassistant.documentparse.controller;

import com.aiassistant.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/document")
public class ParseController {

    @PostMapping("/parse")
    public R<String> parse(@RequestBody byte[] fileContent,
                           @RequestParam String fileName) {
        log.info("Received parse request for file: {}", fileName);
        return R.ok("Parse endpoint reached");
    }
}
