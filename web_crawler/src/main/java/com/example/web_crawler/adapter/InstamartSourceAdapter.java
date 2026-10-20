package com.example.web_crawler.adapter;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class InstamartSourceAdapter extends HtmlRetailerSourceAdapter {
    public InstamartSourceAdapter(WebClient webClient) {
        super("http://localhost:8088", webClient);
    }

    @Override public String getSourceKey() { return "instamart"; }
    @Override public String getDisplayName() { return "Swiggy Instamart"; }
    @Override public boolean isEnabled() { return true; }
}