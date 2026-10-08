-- Product 1 is the flash sale item. Stock is large so the sale never sells out mid-test:
-- what is measured is the checkout, not running out.
INSERT INTO products (id, name, price_cents, stock)
VALUES (1, 'Wireless Earbuds, flash sale', 2999, 1000000);

INSERT INTO products (id, name, price_cents, stock)
SELECT g, 'Product ' || g, 1000 + g, 500
FROM generate_series(2, 1000) AS g;
