package org.java.guba.reactiveprogramming.sec07.client;

import org.java.guba.reactiveprogramming.common.AbstractHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class ExternalServiceClient extends AbstractHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalServiceClient.class);

    public Mono<String> getProductName(int productId) {
        return this.httpClient.get()
                .uri("/demo01/product/" + productId)
                .responseContent()
                .asString()
                .doOnNext(m -> log.info("next: {}", m))
                .next();
    }

}
