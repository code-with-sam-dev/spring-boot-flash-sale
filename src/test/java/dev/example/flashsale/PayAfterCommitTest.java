package dev.example.flashsale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "checkout.mode=pay-after-commit")
class PayAfterCommitTest extends CheckoutContract {

    @Test
    void isTheCheckoutInUse() {
        assertThat(checkout).isInstanceOf(PayAfterCommit.class);
    }
}
