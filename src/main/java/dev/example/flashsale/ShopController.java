package dev.example.flashsale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShopController {

    private final ProductRepository products;
    private final Checkout checkout;

    public ShopController(ProductRepository products, Checkout checkout) {
        this.products = products;
        this.checkout = checkout;
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> product(@PathVariable long id) {
        return ResponseEntity.of(products.find(id));
    }

    @PostMapping("/orders")
    public OrderPlaced order(@RequestBody OrderRequest request) {
        return checkout.placeOrder(request);
    }

    @ExceptionHandler(SoldOut.class)
    public ResponseEntity<String> soldOut(SoldOut e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
