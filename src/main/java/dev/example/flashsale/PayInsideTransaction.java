package dev.example.flashsale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The version most of us write first: one transaction around the whole checkout,
 * including the call to the card provider.
 */
@Service
@ConditionalOnProperty(name = "checkout.mode", havingValue = "pay-inside", matchIfMissing = true)
public class PayInsideTransaction implements Checkout {

    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentGateway payments;

    public PayInsideTransaction(ProductRepository products, OrderRepository orders, PaymentGateway payments) {
        this.products = products;
        this.orders = orders;
        this.payments = payments;
    }

    @Override
    @Transactional
    public OrderPlaced placeOrder(OrderRequest request) {
        if (!products.takeOne(request.productId())) {
            throw new SoldOut(request.productId());
        }
        int amount = products.price(request.productId());
        String ref = payments.charge(request.customer(), amount);
        long id = orders.insert(request.productId(), request.customer(), amount, "PAID");
        return new OrderPlaced(id, request.productId(), ref);
    }
}
