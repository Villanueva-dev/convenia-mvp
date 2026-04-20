package com.uniremington.api.convenia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient} bean used for Documenso API calls.
 *
 * <p>Sets the base URL and Authorization header once so that individual
 * service calls do not need to repeat them.</p>
 */
@Configuration
public class DocumensoConfig {

    @Value("${app.documenso.base-url}")
    private String baseUrl;

    @Value("${app.documenso.token}")
    private String token;

    /**
     * Pre-configured {@link RestClient} for all Documenso REST API calls.
     *
     * @return a {@link RestClient} with base URL and Bearer token set.
     */
    @Bean("documensoRestClient")
    public RestClient documensoRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }
}
