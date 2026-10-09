package dev.example.flashsale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcClient db;

    public OrderRepository(JdbcClient db) {
        this.db = db;
    }

    public long insert(long productId, String customer, int amountCents,
                       String status) {
        return db.sql("""
                INSERT INTO orders (product_id, customer, amount_cents, status)
                VALUES (:product, :customer, :amount, :status)
                RETURNING id""")
                .param("product", productId)
                .param("customer", customer)
                .param("amount", amountCents)
                .param("status", status)
                .query(Long.class)
                .single();
    }

    public void markPaid(long orderId, String paymentRef) {
        db.sql("UPDATE orders SET status = 'PAID', payment_ref = :ref WHERE id = :id")
                .param("ref", paymentRef)
                .param("id", orderId)
                .update();
    }
}
