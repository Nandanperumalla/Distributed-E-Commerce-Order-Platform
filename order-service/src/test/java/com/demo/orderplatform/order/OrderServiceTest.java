package com.demo.orderplatform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.order.api.PlaceOrderRequest;
import com.demo.orderplatform.order.service.CatalogClient;
import com.demo.orderplatform.order.service.CatalogExceptions;
import com.demo.orderplatform.order.service.CatalogItem;
import com.demo.orderplatform.order.service.IdempotencyKeys;
import com.demo.orderplatform.order.service.OrderService;
import com.demo.orderplatform.order.service.OrderWriter;
import com.demo.orderplatform.order.service.PlacedOrder;
import com.demo.orderplatform.order.store.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private IdempotencyKeys idempotency;
    @Mock
    private CatalogClient catalog;
    @Mock
    private OrderWriter writer;
    @Mock
    private OrderRepository repository;

    private OrderService service() {
        return new OrderService(idempotency, catalog, writer, repository);
    }

    @Test
    void aReplayedKeyReturnsTheOriginalOrderAndWritesNothing() {
        String original = UUID.randomUUID().toString();
        when(idempotency.claim(eq("key-1"), anyString())).thenReturn(Optional.of(original));

        PlacedOrder placed = service().place("key-1", request(List.of(line("SKU-A", 1))));

        assertThat(placed.orderId()).isEqualTo(original);
        assertThat(placed.replayed()).isTrue();
        verify(writer, never()).create(any(), anyString(), any(), anyLong());
    }

    @Test
    void repeatedSkusAreFoldedIntoOneLineAndPricedFromTheCatalog() {
        when(idempotency.claim(anyString(), anyString())).thenReturn(Optional.empty());
        when(catalog.lookup("SKU-A")).thenReturn(new CatalogItem("SKU-A", "Thing", 100, 2_500L));

        PlacedOrder placed = service().place("key-2", request(List.of(line("SKU-A", 2), line("SKU-A", 3))));

        assertThat(placed.replayed()).isFalse();

        ArgumentCaptor<List<OrderLine>> lines = ArgumentCaptor.captor();
        ArgumentCaptor<Long> total = ArgumentCaptor.captor();
        verify(writer).create(any(UUID.class), eq("cust-1"), lines.capture(), total.capture());

        assertThat(lines.getValue()).containsExactly(new OrderLine("SKU-A", 5, 2_500L));
        assertThat(total.getValue()).isEqualTo(12_500L);
    }

    @Test
    void aFailedWriteHandsTheIdempotencyKeyBack() {
        when(idempotency.claim(anyString(), anyString())).thenReturn(Optional.empty());
        when(catalog.lookup("SKU-GONE")).thenThrow(new CatalogExceptions.UnknownSku("SKU-GONE"));

        assertThatThrownBy(() -> service().place("key-3", request(List.of(line("SKU-GONE", 1)))))
                .isInstanceOf(CatalogExceptions.UnknownSku.class);

        // Otherwise the client's retry would be told about an order that was
        // never created.
        verify(idempotency).release("key-3");
    }

    private static PlaceOrderRequest request(List<PlaceOrderRequest.Line> lines) {
        return new PlaceOrderRequest("cust-1", lines);
    }

    private static PlaceOrderRequest.Line line(String sku, int quantity) {
        return new PlaceOrderRequest.Line(sku, quantity);
    }
}
