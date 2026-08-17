package com.demo.orderplatform.order.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The one synchronous hop in the flow: order-service asks inventory-service what
 * a SKU costs before it will price an order. Stock levels are only advisory here
 * — the authoritative check is the conditional UPDATE inventory-service runs
 * inside its own transaction.
 */
@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    public CatalogItem lookup(String sku) {
        try {
            CatalogItem item = restClient.get()
                    .uri("/inventory/{sku}", sku)
                    .retrieve()
                    .body(CatalogItem.class);
            if (item == null) {
                throw new CatalogExceptions.CatalogUnavailable("empty catalog response for " + sku, null);
            }
            return item;
        } catch (HttpClientErrorException.NotFound e) {
            throw new CatalogExceptions.UnknownSku(sku);
        } catch (RestClientException e) {
            throw new CatalogExceptions.CatalogUnavailable("catalog lookup failed for " + sku, e);
        }
    }
}
