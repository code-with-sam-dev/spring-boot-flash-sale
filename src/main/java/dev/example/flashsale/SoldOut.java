package dev.example.flashsale;

public class SoldOut extends RuntimeException {

    public SoldOut(long productId) {
        super("Product " + productId + " is sold out");
    }
}
