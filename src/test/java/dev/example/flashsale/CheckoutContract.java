package dev.example.flashsale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Both checkouts must do the same thing to the data. Only how long they hold a connection differs. */
@Testcontainers
abstract class CheckoutContract {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired Checkout checkout;
    @Autowired JdbcClient db;
    @MockitoBean PaymentGateway payments;

    @BeforeEach
    void reset() {
        db.sql("DELETE FROM orders").update();
        db.sql("UPDATE products SET stock = 3 WHERE id = 1").update();
        when(payments.charge(anyString(), anyString(), anyInt())).thenReturn("pay_test");
    }

    @Test
    void takesOneUnitAndRecordsOnePaidOrder() {
        OrderPlaced placed = checkout.placeOrder(new OrderRequest(1, "Sarah Thompson"));

        assertThat(placed.paymentRef()).isEqualTo("pay_test");
        assertThat(stock()).isEqualTo(2);
        assertThat(db.sql("SELECT status FROM orders WHERE id = :id").param("id", placed.orderId())
                .query(String.class).single()).isEqualTo("PAID");
    }

    @Test
    void refusesWhenSoldOut() {
        db.sql("UPDATE products SET stock = 0 WHERE id = 1").update();

        assertThatThrownBy(() -> checkout.placeOrder(new OrderRequest(1, "James")))
                .isInstanceOf(SoldOut.class);
        assertThat(db.sql("SELECT count(*) FROM orders").query(Long.class).single()).isZero();
    }

    int stock() {
        return db.sql("SELECT stock FROM products WHERE id = 1").query(Integer.class).single();
    }
}
