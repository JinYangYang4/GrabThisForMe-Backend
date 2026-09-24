package com.study.grabthisforme;
import static org.assertj.core.api.Assertions.*;
import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.OrderService;
import com.study.grabthisforme.controller.OrderController.CreateOrderRequest;
import com.study.grabthisforme.common.ApiException;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class OrderLifecycleTests {
    @Autowired OrderService orders;
    @Autowired AuthService auth;
    long user() { return auth.register("test_"+UUID.randomUUID(),"Testpass123", "Tester", null,null).user().id(); }
    CreateOrderRequest request(String key) {
        long now=System.currentTimeMillis();
        return new CreateOrderRequest(key,"Parcel", "private instructions",0.0,"","secret-code","Dorm 201","Locker 2",now,now+3600000,2,"BUY_GOODS");
    }
    @Test void lifecycleIsRoleRestrictedAndPublicPreviewIsRedacted() {
        long buyer=user(), courier=user(), outsider=user();
        var created=orders.createOrder(buyer,request(UUID.randomUUID().toString()));
        var preview=orders.getOrder(courier,created.orderId());
        assertThat(preview.shelfNumber()).isEmpty();
        assertThat(preview.goodsMessage()).doesNotContain("private");
        assertThatThrownBy(()->orders.acceptOrder(buyer,created.orderId())).isInstanceOf(ApiException.class);
        assertThat(orders.acceptOrder(courier,created.orderId()).orderStatus()).isEqualTo(1);
        assertThatThrownBy(()->orders.getOrder(outsider,created.orderId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->orders.updateStatus(courier,created.orderId(),2)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->orders.updateStatus(buyer,created.orderId(),2)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->orders.updateStatus(buyer,created.orderId(),4)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->orders.updateStatus(outsider,created.orderId(),999)).isInstanceOf(ApiException.class);
        assertThat(orders.updateStatus(courier,created.orderId(),3).orderStatus()).isEqualTo(3);
        assertThat(orders.updateStatus(buyer,created.orderId(),2).orderStatus()).isEqualTo(2);
        assertThat(orders.updateStatus(buyer,created.orderId(),2).orderStatus()).isEqualTo(2);
        assertThatThrownBy(()->orders.acceptOrder(outsider,created.orderId())).isInstanceOf(ApiException.class);
    }
    @Test void retriesCreateOnlyOneOrderAndCancelIsTerminal() {
        long buyer=user(), courier=user(); var r=request(UUID.randomUUID().toString());
        var a=orders.createOrder(buyer,r); var b=orders.createOrder(buyer,r);
        assertThat(a.orderId()).isEqualTo(b.orderId());
        assertThat(a.quantity()).isEqualTo(2);
        assertThat(a.errandType()).isEqualTo("BUY_GOODS");
        var changed=new CreateOrderRequest(r.clientOrderId(),"Different",r.goodsMessage(),r.goodsPrice(),r.goodsPic(),r.shelfNumber(),r.aimPosition(),r.atPosition(),r.startTime(),r.endTime(),2,"BUY_GOODS");
        assertThatThrownBy(()->orders.createOrder(buyer,changed)).isInstanceOf(ApiException.class);
        assertThat(orders.listOrders(buyer,"all")).hasSize(1);
        assertThat(orders.updateStatus(buyer,a.orderId(),4).orderStatus()).isEqualTo(4);
        assertThatThrownBy(()->orders.acceptOrder(courier,a.orderId())).isInstanceOf(ApiException.class);
    }
    @Test void twoCouriersCannotWinTheSameOrder() throws Exception {
        long buyer=user(), first=user(), second=user();
        var a=orders.createOrder(buyer,request(UUID.randomUUID().toString()));
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Boolean> one=()->{ start.await(); try {orders.acceptOrder(first,a.orderId());return true;} catch(ApiException e){return false;} };
            Callable<Boolean> two=()->{ start.await(); try {orders.acceptOrder(second,a.orderId());return true;} catch(ApiException e){return false;} };
            var f=pool.submit(one); var s=pool.submit(two); start.countDown();
            assertThat((f.get(15,TimeUnit.SECONDS)?1:0)+(s.get(15,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void availableOrdersCanBeFilteredByErrandType() {
        long buyer=user(), courier=user();
        long now=System.currentTimeMillis();
        var buy=new CreateOrderRequest(UUID.randomUUID().toString(),"Milk","",5.0,"","","Dorm 201","Shop",now,now+3600000,1,"BUY_GOODS");
        var express=new CreateOrderRequest(UUID.randomUUID().toString(),"快递代取 - 校园驿站","",0.0,"","A1","Dorm 201","Locker",now,now+3600000,1,"PICKUP_EXPRESS");
        orders.createOrder(buyer,buy);
        orders.createOrder(buyer,express);

        assertThat(orders.available(courier,0,50,"BUY_GOODS")).extracting(v -> v.errandType()).contains("BUY_GOODS").doesNotContain("PICKUP_EXPRESS");
        assertThat(orders.available(courier,0,50,"PICKUP_EXPRESS")).extracting(v -> v.errandType()).contains("PICKUP_EXPRESS").doesNotContain("BUY_GOODS");
    }
}
