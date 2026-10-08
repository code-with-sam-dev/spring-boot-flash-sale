package dev.example.flashsale;

public record OrderPlaced(long orderId, long productId, String paymentRef) {
}
