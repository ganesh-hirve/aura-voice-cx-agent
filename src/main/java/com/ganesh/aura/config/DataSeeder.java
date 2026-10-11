package com.ganesh.aura.config;

import com.ganesh.aura.entity.Order;
import com.ganesh.aura.repository.OrderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedOrders(OrderRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            Order order1 = createOrder(
                    "ORD-101", "Priya Sharma", "Vitamin C Serum (30ml)", "699",
                    "Out for Delivery", "BlueDart", "BD-982103",
                    LocalDate.now(), null, now);
            Order order2 = createOrder(
                    "ORD-102", "Rahul Verma", "Hydrating Sunscreen SPF 50", "499",
                    "Delivered", "Delhivery", "DL-441029",
                    null, now.minusDays(14), now.minusDays(14));
            Order order3 = createOrder(
                    "ORD-103", "Ananya Patel", "Green Tea Face Wash + Toner", "850",
                    "Processing", null, null,
                    null, null, now.minusHours(3));

            repository.saveAll(List.of(order1, order2, order3));
        };
    }

    private Order createOrder(
            String orderId,
            String customerName,
            String product,
            String amount,
            String status,
            String courier,
            String trackingNumber,
            LocalDate expectedDelivery,
            LocalDateTime deliveredAt,
            LocalDateTime createdAt) {
        Order order = createEmptyOrder();
        setField(order, "orderId", orderId);
        setField(order, "customerName", customerName);
        setField(order, "product", product);
        setField(order, "amount", new BigDecimal(amount));
        setField(order, "status", status);
        setField(order, "courier", courier);
        setField(order, "trackingNumber", trackingNumber);
        setField(order, "expectedDelivery", expectedDelivery);
        setField(order, "deliveredAt", deliveredAt);
        setField(order, "createdAt", createdAt);
        return order;
    }

    private Order createEmptyOrder() {
        try {
            var constructor = Order.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create mock order", e);
        }
    }

    private void setField(Order order, String name, Object value) {
        try {
            Field field = Order.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(order, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to initialize mock order field: " + name, e);
        }
    }
}
