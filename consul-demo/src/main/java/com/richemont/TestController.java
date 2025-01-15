package com.richemont;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
public class TestController {

    private static final Logger log = LogManager.getLogger(TestController.class);

    @Value("${test:}")
    private String test;

    @GetMapping("/test")
    public String test() {
        return test;
    }

    @PostMapping("/callback")
    public void callback(@RequestBody String body) {
        log.info("callback: {}", body);
    }

}
