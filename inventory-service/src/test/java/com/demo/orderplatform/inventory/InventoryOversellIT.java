package com.demo.orderplatform.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.inventory.domain.InsufficientStockException;
import com.demo.orderplatform.inventory.service.ReservationService;
import com.demo.orderplatform.inventory.store.InventoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The claim under test: concurrent orders for the same SKU cannot oversell it.
 *
 * <p>Integration tests that exercise the database are the only way to prove this.
 * Our components handle the transaction boundary with `@Transactional` —
 * which does nothing without a Spring proxy — so the calls here are wrapped in a
 * {@link TransactionTemplate}, giving exactly the boundary the proxy would.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092", // Mock out Kafka
})
@Testcontainers
class InventoryOversellIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static ReservationService reservations;

    @BeforeAll
    static void startDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbc = new JdbcTemplate(dataSource);
        reservations = new ReservationService(new InventoryRepository(jdbc));
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void resetStock() {
        jdbc.update("DELETE FROM reservation_items");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("UPDATE inventory SET available = 10, reserved = 0 WHERE sku = 'SKU-LAPTOP'");
    }

    @Test
    void fortyOrdersChasingTenUnitsLeaveExactlyTenWinners() throws Exception {
        int stock = 10;
        int contenders = 40;

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>(contenders);

        for (int i = 0; i < contenders; i++) {
            String orderId = UUID.randomUUID().toString();
            attempts.add(pool.submit(() -> {
                startGun.await();
                return reserveOne(orderId);
            }));
        }

        startGun.countDown();

        long reserved = 0;
        for (Future<Boolean> attempt : attempts) {
            if (attempt.get(30, TimeUnit.SECONDS)) {
                reserved++;
            }
        }
        pool.shutdown();

        assertThat(reserved)
                .as("exactly the available stock should be handed out, no more")
                .isEqualTo(stock);
        assertThat(available("SKU-LAPTOP")).isZero();
        assertThat(reservedCount("SKU-LAPTOP")).isEqualTo(stock);
    }

    @Test
    void redeliveringTheSameOrderDoesNotTakeStockTwice() {
        String orderId = UUID.randomUUID().toString();

        assertThat(reserveOne(orderId)).isTrue();
        assertThat(available("SKU-LAPTOP")).isEqualTo(9);

        // Same event, delivered again — at-least-once is the guarantee Kafka gives.
        assertThat(reserveOne(orderId)).isTrue();
        assertThat(available("SKU-LAPTOP"))
                .as("a replayed event must not decrement a second time")
                .isEqualTo(9);
    }

    @Test
    void aRejectedOrderLeavesEveryLineUntouched() {
        String orderId = UUID.randomUUID().toString();

        boolean ok = reserve(orderId, List.of(
                new OrderLine("SKU-LAPTOP", 3, 0),
                new OrderLine("SKU-LAST-ONE", 99, 0)));

        assertThat(ok).isFalse();
        assertThat(available("SKU-LAPTOP"))
                .as("the laptop line must roll back with the mug line")
                .isEqualTo(10);
        assertThat(available("SKU-LAST-ONE")).isEqualTo(1);
    }

    @Test
    void releasingAReservationPutsTheStockBackExactlyOnce() {
        String orderId = UUID.randomUUID().toString();
        assertThat(reserve(orderId, List.of(new OrderLine("SKU-LAPTOP", 4, 0)))).isTrue();
        assertThat(available("SKU-LAPTOP")).isEqualTo(6);

        List<OrderLine> released = tx.execute(status -> reservations.release(orderId));
        assertThat(released).hasSize(1);
        assertThat(available("SKU-LAPTOP")).isEqualTo(10);

        // Compensation is idempotent too.
        List<OrderLine> releasedAgain = tx.execute(status -> reservations.release(orderId));
        assertThat(releasedAgain).isEmpty();
        assertThat(available("SKU-LAPTOP")).isEqualTo(10);
    }

    private boolean reserveOne(String orderId) {
        return reserve(orderId, List.of(new OrderLine("SKU-LAPTOP", 1, 0)));
    }

    private boolean reserve(String orderId, List<OrderLine> lines) {
        try {
            tx.execute(status -> reservations.reserve(orderId, lines));
            return true;
        } catch (InsufficientStockException e) {
            return false;
        }
    }

    private int available(String sku) {
        Integer value = jdbc.queryForObject(
                "SELECT available FROM inventory WHERE sku = ?", Integer.class, sku);
        return value == null ? -1 : value;
    }

    private int reservedCount(String sku) {
        Integer value = jdbc.queryForObject(
                "SELECT reserved FROM inventory WHERE sku = ?", Integer.class, sku);
        return value == null ? -1 : value;
    }
}
