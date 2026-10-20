
package com.example.web_crawler.adapter;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FlipkartSourceAdapter extends HtmlRetailerSourceAdapter {

    public FlipkartSourceAdapter(WebClient webClient) {
        super("http://localhost:8086", webClient);
    }

    @Override
    public String getSourceKey() {
        return "flipkart";
    }

    @Override
    public String getDisplayName() {
        return "Flipkart";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
