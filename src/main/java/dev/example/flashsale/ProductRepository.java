package dev.example.flashsale;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final JdbcClient db;

    public ProductRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<Product> find(long id) {
        return db.sql("""
                SELECT id, name, price_cents, stock FROM products WHERE id = :id""")
                .param("id", id)
                .query(Product.class)
                .optional();
    }

    /** Takes one unit of stock. The row stays locked until the transaction ends. */
    public boolean takeOne(long id) {
        return db.sql("""
                UPDATE products SET stock = stock - 1
                WHERE id = :id AND stock > 0""")
                .param("id", id)
                .update() == 1;
    }

    public int price(long id) {
        return db.sql("SELECT price_cents FROM products WHERE id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }
}
