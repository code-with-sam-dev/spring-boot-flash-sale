package dev.example.flashsale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reserve the stock and commit, then call the card provider with no
 * connection and no lock held, then record the payment in a second short transaction.
 */
@Service
@ConditionalOnProperty(name = "checkout.mode", havingValue = "pay-after-commit")
public class PayAfterCommit implements Checkout {

    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentGateway payments;
    private final TransactionTemplate tx;

    public PayAfterCommit(ProductRepository products, OrderRepository orders,
                          PaymentGateway payments, TransactionTemplate tx) {
        this.products = products;
        this.orders = orders;
        this.payments = payments;
        this.tx = tx;
    }

    @Override
    public OrderPlaced placeOrder(OrderRequest request) {
        record Reserved(long orderId, int amount) {
        }
        Reserved reserved = tx.execute(status -> {
            if (!products.takeOne(request.productId())) {
                throw new SoldOut(request.productId());
            }
            int amount = products.price(request.productId());
            return new Reserved(orders.insert(request.productId(), request.customer(), amount, "RESERVED"), amount);
        });
        String ref = payments.charge("order-" + reserved.orderId(), request.customer(), reserved.amount());
        tx.executeWithoutResult(status -> orders.markPaid(reserved.orderId(), ref));
        return new OrderPlaced(reserved.orderId(), request.productId(), ref);
    }
}
