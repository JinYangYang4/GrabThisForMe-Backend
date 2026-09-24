package com.study.grabthisforme;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.controller.OrderController.CreateOrderRequest;
import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderRecipientPrivacyTests {
    @Autowired OrderService orders;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;

    long user() { return auth.register("addr_" + UUID.randomUUID(), "Testpass123", "Tester", null, null).user().id(); }
    CreateOrderRequest request(String key, String name, String phone) {
        return new CreateOrderRequest(key, "Parcel", "", 0.0, "", "", "Campus Building 2", "Locker",
            0L, 0L, 1, "PICKUP_EXPRESS", name, phone);
    }

    @Test void contactsAreHiddenUntilAcceptanceAndRemainAnOrderSnapshot() throws Exception {
        long buyer = user(), courier = user(), outsider = user();
        var request = request(UUID.randomUUID().toString(), "收件同学", "15500000000");
        var order = orders.createOrder(buyer, request);
        assertThat(order.recipientName()).isEqualTo("收件同学");
        assertThat(order.recipientPhone()).isEqualTo("15500000000");
        var preview = orders.getOrder(courier, order.orderId());
        assertThat(preview.recipientName()).isEmpty();
        assertThat(preview.recipientPhone()).isEmpty();
        assertThat(json.writeValueAsString(preview)).doesNotContain("15500000000", "收件同学");
        var listed = orders.available(outsider, 0, 100, "PICKUP_EXPRESS").stream()
            .filter(row -> row.orderId().equals(order.orderId())).findFirst().orElseThrow();
        assertThat(listed.recipientPhone()).isEmpty();
        assertThat(orders.acceptOrder(courier, order.orderId()).recipientPhone()).isEqualTo("15500000000");
        assertThat(orders.listOrders(courier, "sender")).filteredOn(row -> row.orderId().equals(order.orderId()))
            .allSatisfy(row -> assertThat(row.recipientName()).isEqualTo("收件同学"));
        assertThatThrownBy(() -> orders.getOrder(outsider, order.orderId())).isInstanceOf(ApiException.class);
        // A different contact is a different request, not an overwrite of the existing order.
        assertThatThrownBy(() -> orders.createOrder(buyer, request(request.clientOrderId(), "另一位", "16600000000")))
            .isInstanceOf(ApiException.class);
        assertThat(orders.getOrder(buyer, order.orderId()).recipientPhone()).isEqualTo("15500000000");
    }

    @Test void retriesKeepTheSameContactAndOrder() {
        long buyer = user();
        var request = request(UUID.randomUUID().toString(), "同学", "+85255551234");
        var first = orders.createOrder(buyer, request);
        assertThat(orders.createOrder(buyer, request)).isEqualTo(first);
    }

    @Test void invalidOrPartialContactsAreRejectedEvenAtTheServiceBoundary() {
        long buyer = user();
        for (String phone : new String[] {"abc", "123", "15500000000\n", "+1234567890123456"}) {
            // Trimmed surrounding whitespace is accepted; embedded controls are not.
            if (phone.endsWith("\n")) phone = "1550\n0000000";
            var candidate = request(UUID.randomUUID().toString(), "同学", phone);
            assertThatThrownBy(() -> orders.createOrder(buyer, candidate)).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> orders.createOrder(buyer, request(UUID.randomUUID().toString(), "", "15500000000")))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> orders.createOrder(buyer, request(UUID.randomUUID().toString(), "同\n学", "15500000000")))
            .isInstanceOf(ApiException.class);
    }

    @Test void legacyRequestsKeepTheirSerializationAndCanStillBeRetried() throws Exception {
        long buyer = user();
        var legacy = new CreateOrderRequest(UUID.randomUUID().toString(), "Milk", "", 1.0, "", "",
            "Dorm", "Shop", 0L, 0L, 1, "BUY_GOODS");
        assertThat(json.writeValueAsString(legacy)).doesNotContain("recipientName", "recipientPhone");
        var created = orders.createOrder(buyer, legacy);
        assertThat(created.recipientName()).isEmpty();
        assertThat(orders.createOrder(buyer, legacy).orderId()).isEqualTo(created.orderId());
    }
}
