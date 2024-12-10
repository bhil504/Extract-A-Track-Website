package com.BhillionDollarApps.extrack_a_track.filters;



import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Component
public class CSPFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Generate a unique nonce for this response
        String nonce = UUID.randomUUID().toString();

        // Add the CSP header with the nonce
        httpResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' https://cdn.jsdelivr.net 'nonce-" + nonce + "'; " +
                "style-src 'self' https://cdn.jsdelivr.net 'nonce-" + nonce + "' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "font-src 'self' https://cdn.jsdelivr.net; " +
                "connect-src 'self'; " +
                "frame-src 'none'; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'; " +
                "upgrade-insecure-requests; " +
                "block-all-mixed-content;");

        // Add the nonce as a request attribute for use in templates
        ((HttpServletRequest) request).setAttribute("cspNonce", nonce);

        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
