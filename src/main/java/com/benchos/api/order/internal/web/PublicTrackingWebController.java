package com.benchos.api.order.internal.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class PublicTrackingWebController {

    private final String trackingHtml;
    private final String receiptHtml;

    public PublicTrackingWebController() throws IOException {
        this.trackingHtml = new ClassPathResource("static/track.html")
                .getContentAsString(StandardCharsets.UTF_8);
        this.receiptHtml = new ClassPathResource("static/receipt.html")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/track/{publicToken}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> renderTrackingPage(@PathVariable String publicToken) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache())
                .body(trackingHtml);
    }

    @GetMapping(value = "/track/{publicToken}/receipt", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> renderReceiptPage(@PathVariable String publicToken) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache())
                .body(receiptHtml);
    }
}
