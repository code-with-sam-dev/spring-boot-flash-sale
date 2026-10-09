package dev.example.flashsale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = "checkout.mode=pay-after-commit")
class PayAfterCommitTest extends CheckoutContract {

    @MockitoSpyBean OrderRepository orders;

    @Test
    void isTheCheckoutInUse() {
        assertThat(checkout).isInstanceOf(PayAfterCommit.class);
    }

    @Test
    void chargedButNotRecordedLeavesAReservedOrderToReconcile() {
        doThrow(new DataAccessResourceFailureException("database went away"))
                .when(orders).markPaid(anyLong(), anyString());

        var order = new OrderRequest(1, "Sarah Thompson");
        assertThatThrownBy(() -> checkout.placeOrder(order))
                .isInstanceOf(DataAccessResourceFailureException.class);

        long id = db.sql("SELECT id FROM orders").query(Long.class).single();
        assertThat(db.sql("SELECT status FROM orders WHERE id = :id").param("id", id)
                .query(String.class).single()).isEqualTo("RESERVED");
        assertThat(stock()).isEqualTo(2);
        verify(payments).charge(eq("order-" + id), eq("Sarah Thompson"), eq(2999));
    }
}
