package com.example.web_crawler.adapter;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AmazonSourceAdapter extends HtmlRetailerSourceAdapter {
    public AmazonSourceAdapter(WebClient webClient) {
        super("http://localhost:8085", webClient);
    }

    @Override public String getSourceKey() { return "amazon"; }
    @Override public String getDisplayName() { return "Amazon India"; }
    @Override public boolean isEnabled() { return true; }
}