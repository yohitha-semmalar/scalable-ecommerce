package com.example.web_crawler.adapter;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class MeeshoSourceAdapter extends HtmlRetailerSourceAdapter {
    public MeeshoSourceAdapter(WebClient webClient) {
        super("http://localhost:8087", webClient);
    }

    @Override public String getSourceKey() { return "meesho"; }
    @Override public String getDisplayName() { return "Meesho"; }
    @Override public boolean isEnabled() { return true; }
}