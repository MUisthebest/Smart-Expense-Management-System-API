package com.example.smartmoney.domain.models.user.valueobjects;

import com.example.smartmoney.domain.exceptions.DomainException;

public class Avatar {
    private final String url;

    protected Avatar() {
        this.url = null;
    }    
    
    public Avatar(String url) {
        if (url == null || url.isEmpty()) {
            this.url = null; 
            return;
        }
        
        if (!isValidUrl(url)) {
            throw new DomainException("Invalid avatar URL");
        }
        this.url = url;
    }
    
    private boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
    
    public String getUrl() {
        return url;
    }
}