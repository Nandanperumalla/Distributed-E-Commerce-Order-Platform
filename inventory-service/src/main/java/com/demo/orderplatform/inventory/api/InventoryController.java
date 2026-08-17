package com.demo.orderplatform.inventory.api;

import com.demo.orderplatform.inventory.domain.StockItem;
import com.demo.orderplatform.inventory.service.StockCatalog;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The REST half of the system: order-service prices orders through this. */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final StockCatalog catalog;

    public InventoryController(StockCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<StockItem> all() {
        return catalog.all();
    }

    @GetMapping("/{sku}")
    public ResponseEntity<StockItem> one(@PathVariable String sku) {
        return catalog.lookup(sku)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
