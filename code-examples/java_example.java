/**
 * Enterprise Microservices Architecture - Order Management System
 * Author: Rasya Andrean
 * Description: Scalable order processing system with Spring Boot and reactive programming
 */

package com.rasyaandrean.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.persistence.*;
import javax.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.math.BigDecimal;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

@SpringBootApplication
@EnableEurekaClient
@Slf4j
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
        log.info("🚀 Order Service started successfully!");
    }
}

// Domain Models
@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderNumber;

    @NotNull
    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @Embedded
    private ShippingAddress shippingAddress;

    @Embedded
    private BillingAddress billingAddress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveredAt;

    @Column(length = 1000)
    private String notes;

    @ElementCollection
    @CollectionTable(name = "order_tags", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (orderNumber == null) {
            orderNumber = generateOrderNumber();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" +
               (int)(Math.random() * 1000);
    }

    public BigDecimal calculateFinalAmount() {
        BigDecimal subtotal = totalAmount;
        if (discountAmount != null) {
            subtotal = subtotal.subtract(discountAmount);
        }
        if (taxAmount != null) {
            subtotal = subtotal.add(taxAmount);
        }
        if (shippingCost != null) {
            subtotal = subtotal.add(shippingCost);
        }
        return subtotal;
    }
}

@Entity
@Table(name = "order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @NotNull
    @Column(nullable = false)
    private String productId;

    @NotBlank
    @Column(nullable = false)
    private String productName;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(length = 500)
    private String specifications;

    public BigDecimal getTotalPrice() {
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (discountAmount != null) {
            total = total.subtract(discountAmount);
        }
        return total;
    }
}

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {
    @NotBlank
    private String fullName;

    @NotBlank
    private String addressLine1;

    private String addressLine2;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    private String postalCode;

    @NotBlank
    private String country;

    private String phoneNumber;
}

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingAddress {
    @NotBlank
    private String fullName;

    @NotBlank
    private String addressLine1;

    private String addressLine2;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    private String postalCode;

    @NotBlank
    private String country;
}

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}

// DTOs
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Validated
public class CreateOrderRequest {
    @NotNull
    private String customerId;

    @NotEmpty
    private List<OrderItemRequest> items;

    @NotNull
    @Valid
    private ShippingAddress shippingAddress;

    @Valid
    private BillingAddress billingAddress;

    private String notes;
    private Set<String> tags;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {
    @NotNull
    private String productId;

    @NotBlank
    private String productName;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal unitPrice;

    private BigDecimal discountAmount;
    private String specifications;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private String customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal finalAmount;
    private List<OrderItemResponse> items;
    private ShippingAddress shippingAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String notes;
    private Set<String> tags;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private String productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String specifications;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateRequest {
    @NotNull
    private OrderStatus status;
    private String notes;
}

// Repository Layer
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findOrdersByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT o FROM Order o WHERE o.totalAmount >= :minAmount AND o.totalAmount <= :maxAmount")
    List<Order> findOrdersByAmountRange(
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount
    );

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customerId = :customerId AND o.status = :status")
    Long countByCustomerIdAndStatus(
        @Param("customerId") String customerId,
        @Param("status") OrderStatus status
    );

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'DELIVERED' AND o.createdAt >= :date")
    BigDecimal getTotalRevenueFromDate(@Param("date") LocalDateTime date);
}

// Service Layer
@Service
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter orderCreatedCounter;
    private final Counter orderCancelledCounter;

    public OrderService(OrderRepository orderRepository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.orderCreatedCounter = Counter.builder("orders.created")
            .description("Number of orders created")
            .register(meterRegistry);
        this.orderCancelledCounter = Counter.builder("orders.cancelled")
            .description("Number of orders cancelled")
            .register(meterRegistry);
    }

    @Timed(value = "order.creation.time", description = "Time taken to create an order")
    public Mono<OrderResponse> createOrder(CreateOrderRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Creating order for customer: {}", request.getCustomerId());

            // Build order entity
            Order order = Order.builder()
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .billingAddress(request.getBillingAddress() != null ?
                    request.getBillingAddress() : request.getShippingAddress())
                .notes(request.getNotes())
                .tags(request.getTags() != null ? request.getTags() : new HashSet<>())
                .build();

            // Build order items
            List<OrderItem> items = request.getItems().stream()
                .map(itemRequest -> OrderItem.builder()
                    .order(order)
                    .productId(itemRequest.getProductId())
                    .productName(itemRequest.getProductName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(itemRequest.getUnitPrice())
                    .discountAmount(itemRequest.getDiscountAmount())
                    .specifications(itemRequest.getSpecifications())
                    .build())
                .collect(Collectors.toList());

            order.setItems(items);

            // Calculate total amount
            BigDecimal totalAmount = items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            order.setTotalAmount(totalAmount);

            // Save order
            Order savedOrder = orderRepository.save(order);

            // Publish event
            publishOrderCreatedEvent(savedOrder);

            // Update metrics
            orderCreatedCounter.increment();

            log.info("Order created successfully: {}", savedOrder.getOrderNumber());

            return mapToOrderResponse(savedOrder);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(error -> log.error("Error creating order", error));
    }

    @Cacheable(value = "orders", key = "#orderNumber")
    public Mono<OrderResponse> getOrderByNumber(String orderNumber) {
        return Mono.fromCallable(() -> {
            log.info("Fetching order: {}", orderNumber);

            return orderRepository.findByOrderNumber(orderNumber)
                .map(this::mapToOrderResponse)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderNumber));
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<OrderResponse> getOrdersByCustomer(String customerId) {
        return Flux.fromIterable(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
            .map(this::mapToOrderResponse)
            .subscribeOn(Schedulers.boundedElastic());
    }

    @CacheEvict(value = "orders", key = "#orderNumber")
    @Timed(value = "order.status.update.time", description = "Time taken to update order status")
    public Mono<OrderResponse> updateOrderStatus(String orderNumber, OrderStatusUpdateRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Updating order status: {} to {}", orderNumber, request.getStatus());

            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderNumber));

            OrderStatus previousStatus = order.getStatus();
            order.setStatus(request.getStatus());

            if (request.getNotes() != null) {
                order.setNotes(request.getNotes());
            }

            if (request.getStatus() == OrderStatus.DELIVERED) {
                order.setDeliveredAt(LocalDateTime.now());
            }

            Order updatedOrder = orderRepository.save(order);

            // Publish status change event
            publishOrderStatusChangedEvent(updatedOrder, previousStatus);

            // Update metrics
            if (request.getStatus() == OrderStatus.CANCELLED) {
                orderCancelledCounter.increment();
            }

            log.info("Order status updated successfully: {}", orderNumber);

            return mapToOrderResponse(updatedOrder);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> cancelOrder(String orderNumber, String reason) {
        return updateOrderStatus(orderNumber,
            OrderStatusUpdateRequest.builder()
                .status(OrderStatus.CANCELLED)
                .notes("Cancelled: " + reason)
                .build())
            .then();
    }

    public Flux<OrderResponse> getOrdersByStatus(OrderStatus status) {
        return Flux.fromIterable(orderRepository.findByStatusOrderByCreatedAtDesc(status))
            .map(this::mapToOrderResponse)
            .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<OrderAnalytics> getOrderAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        return Mono.fromCallable(() -> {
            List<Order> orders = orderRepository.findOrdersByDateRange(startDate, endDate);

            long totalOrders = orders.size();
            BigDecimal totalRevenue = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<OrderStatus, Long> statusCounts = orders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

            BigDecimal averageOrderValue = totalOrders > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, BigDecimal.ROUND_HALF_UP) :
                BigDecimal.ZERO;

            return OrderAnalytics.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(averageOrderValue)
                .statusBreakdown(statusCounts)
                .periodStart(startDate)
                .periodEnd(endDate)
                .build();
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private void publishOrderCreatedEvent(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .customerId(order.getCustomerId())
            .totalAmount(order.getTotalAmount())
            .timestamp(LocalDateTime.now())
            .build();

        kafkaTemplate.send("order-created", event);
        log.info("Published order created event: {}", order.getOrderNumber());
    }

    private void publishOrderStatusChangedEvent(Order order, OrderStatus previousStatus) {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .previousStatus(previousStatus)
            .newStatus(order.getStatus())
            .timestamp(LocalDateTime.now())
            .build();

        kafkaTemplate.send("order-status-changed", event);
        log.info("Published order status changed event: {} -> {}",
            previousStatus, order.getStatus());
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .specifications(item.getSpecifications())
                .build())
            .collect(Collectors.toList());

        return OrderResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .customerId(order.getCustomerId())
            .status(order.getStatus())
            .totalAmount(order.getTotalAmount())
            .finalAmount(order.calculateFinalAmount())
            .items(itemResponses)
            .shippingAddress(order.getShippingAddress())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .notes(order.getNotes())
            .tags(order.getTags())
            .build();
    }
}

// Controller Layer
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Slf4j
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Mono<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Received create order request for customer: {}", request.getCustomerId());
        return orderService.createOrder(request);
    }

    @GetMapping("/{orderNumber}")
    @PreAuthorize("hasRole('USER')")
    public Mono<OrderResponse> getOrder(@PathVariable String orderNumber) {
        log.info("Received get order request: {}", orderNumber);
        return orderService.getOrderByNumber(orderNumber);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('USER') and #customerId == authentication.name")
    public Flux<OrderResponse> getOrdersByCustomer(@PathVariable String customerId) {
        log.info("Received get orders by customer request: {}", customerId);
        return orderService.getOrdersByCustomer(customerId);
    }

    @PutMapping("/{orderNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<OrderResponse> updateOrderStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        log.info("Received update order status request: {} -> {}", orderNumber, request.getStatus());
        return orderService.updateOrderStatus(orderNumber, request);
    }

    @DeleteMapping("/{orderNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> cancelOrder(
            @PathVariable String orderNumber,
            @RequestParam(defaultValue = "Cancelled by admin") String reason) {
        log.info("Received cancel order request: {}", orderNumber);
        return orderService.cancelOrder(orderNumber, reason);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<OrderResponse> getOrdersByStatus(@PathVariable OrderStatus status) {
        log.info("Received get orders by status request: {}", status);
        return orderService.getOrdersByStatus(status);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<OrderAnalytics> getOrderAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Received order analytics request: {} to {}", startDate, endDate);
        return orderService.getOrderAnalytics(startDate, endDate);
    }
}

// Event Models
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private String orderNumber;
    private String customerId;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusChangedEvent {
    private Long orderId;
    private String orderNumber;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private LocalDateTime timestamp;
}

// Analytics Model
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAnalytics {
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;
    private Map<OrderStatus, Long> statusBreakdown;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}

// Event Listeners
@Component
@Slf4j
public class OrderEventListener {

    @KafkaListener(topics = "payment-completed")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment completed event for order: {}", event.getOrderNumber());
        // Process payment completion
    }

    @KafkaListener(topics = "inventory-reserved")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Received inventory reserved event for order: {}", event.getOrderNumber());
        // Process inventory reservation
    }

    @KafkaListener(topics = "shipping-label-created")
    public void handleShippingLabelCreated(ShippingLabelCreatedEvent event) {
        log.info("Received shipping label created event for order: {}", event.getOrderNumber());
        // Process shipping label creation
    }
}

// Exception Classes
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}

public class OrderProcessingException extends RuntimeException {
    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Additional Event Models (simplified)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private String orderNumber;
    private BigDecimal amount;
    private String paymentId;
    private LocalDateTime timestamp;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent {
    private String orderNumber;
    private List<String> productIds;
    private LocalDateTime timestamp;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingLabelCreatedEvent {
    private String orderNumber;
    private String trackingNumber;
    private String carrier;
    private LocalDateTime timestamp;
}

// Security Event Model
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {
    private String eventType;
    private String sourceIp;
    private String userId;
    private String details;
    private String severity;
    private LocalDateTime timestamp;
}

// Enhanced Analytics Model
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedOrderAnalytics {
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;
    private Map<OrderStatus, Long> statusBreakdown;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Map<String, Object> securityMetrics;
    private List<SecurityEvent> recentSecurityEvents;
    private Double anomalyScore; // 0.0 to 1.0
}

// Security Service
@Service
@Slf4j
public class SecurityMonitoringService {

    private final MeterRegistry meterRegistry;
    private final Counter securityEventsCounter;
    private final List<SecurityEvent> securityEvents = new ArrayList<>();

    public SecurityMonitoringService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.securityEventsCounter = Counter.builder("security_events_total")
            .description("Total number of security events")
            .register(meterRegistry);
    }

    public void logSecurityEvent(String eventType, String sourceIp, String userId,
                                String details, String severity) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType(eventType)
            .sourceIp(sourceIp)
            .userId(userId)
            .details(details)
            .severity(severity)
            .timestamp(LocalDateTime.now())
            .build();

        synchronized (securityEvents) {
            securityEvents.add(event);
            // Keep only last 1000 events
            if (securityEvents.size() > 1000) {
                securityEvents.subList(0, securityEvents.size() - 1000).clear();
            }
        }

        securityEventsCounter.increment();
        log.warn("Security event: {} - {} - {} - {}", eventType, severity, sourceIp, details);

        // Record metrics by severity
        Counter.builder("security_events_by_severity")
            .tag("severity", severity)
            .register(meterRegistry)
            .increment();
    }

    public List<SecurityEvent> getRecentSecurityEvents(int limit) {
        synchronized (securityEvents) {
            return securityEvents.stream()
                .sorted((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
        }
    }

    public Map<String, Object> getSecurityMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        synchronized (securityEvents) {
            // Count events by severity
            Map<String, Long> severityCounts = securityEvents.stream()
                .collect(Collectors.groupingBy(
                    SecurityEvent::getSeverity,
                    Collectors.counting()
                ));
            metrics.put("severityCounts", severityCounts);

            // Count events by type
            Map<String, Long> typeCounts = securityEvents.stream()
                .collect(Collectors.groupingBy(
                    SecurityEvent::getEventType,
                    Collectors.counting()
                ));
            metrics.put("typeCounts", typeCounts);

            // Recent events count
            long recentEvents = securityEvents.stream()
                .filter(e -> e.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
                .count();
            metrics.put("recentEvents", recentEvents);
        }

        return metrics;
    }
}

// Enhanced Order Service with Security Monitoring
@Service
@Transactional
@Slf4j
public class EnhancedOrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SecurityMonitoringService securityMonitoringService;
    private final MeterRegistry meterRegistry;

    // Enhanced metrics
    private final Counter ordersCreatedCounter;
    private final Counter orderAnomaliesCounter;
    private final Timer orderProcessingTimer;

    public EnhancedOrderService(
            OrderRepository orderRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            SecurityMonitoringService securityMonitoringService,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.securityMonitoringService = securityMonitoringService;
        this.meterRegistry = meterRegistry;

        // Initialize enhanced metrics
        this.ordersCreatedCounter = Counter.builder("orders_created_total")
            .description("Total number of orders created")
            .tag("enhanced", "true")
            .register(meterRegistry);

        this.orderAnomaliesCounter = Counter.builder("order_anomalies_detected")
            .description("Total number of order anomalies detected")
            .register(meterRegistry);

        this.orderProcessingTimer = Timer.builder("order_processing_duration")
            .description("Order processing duration")
            .register(meterRegistry);
    }

    @Timed
    public Mono<OrderResponse> createOrder(CreateOrderRequest request, String sourceIp, String userId) {
        return Mono.fromCallable(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            try {
                // Security check: Validate request
                validateOrderRequest(request, sourceIp, userId);

                log.info("Creating order for customer: {}", request.getCustomerId());

                // Check for anomalies
                double anomalyScore = calculateAnomalyScore(request);
                if (anomalyScore > 0.8) {
                    String details = String.format("High anomaly score: %.2f for customer %s",
                                                 anomalyScore, request.getCustomerId());
                    securityMonitoringService.logSecurityEvent(
                        "high_order_anomaly", sourceIp, userId, details, "high");
                    orderAnomaliesCounter.increment();
                }

                // Create order logic
                Order order = Order.builder()
                    .orderNumber(generateOrderNumber())
                    .customerId(request.getCustomerId())
                    .status(OrderStatus.PENDING)
                    .items(request.getItems().stream()
                        .map(this::convertToOrderItem)
                        .collect(Collectors.toList()))
                    .shippingAddress(request.getShippingAddress())
                    .billingAddress(request.getBillingAddress())
                    .notes(request.getNotes())
                    .tags(request.getTags())
                    .createdAt(LocalDateTime.now())
                    .build();

                // Calculate total amount
                BigDecimal totalAmount = order.getItems().stream()
                    .map(OrderItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                order.setTotalAmount(totalAmount);

                Order savedOrder = orderRepository.save(order);
                ordersCreatedCounter.increment();

                // Publish event
                OrderCreatedEvent event = OrderCreatedEvent.builder()
                    .orderId(savedOrder.getId())
                    .orderNumber(savedOrder.getOrderNumber())
                    .customerId(savedOrder.getCustomerId())
                    .totalAmount(savedOrder.getTotalAmount())
                    .timestamp(LocalDateTime.now())
                    .build();

                kafkaTemplate.send("order-created", event);

                sample.stop(orderProcessingTimer);
                return convertToResponse(savedOrder);

            } catch (Exception e) {
                sample.stop(orderProcessingTimer);
                throw new OrderProcessingException("Failed to create order", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void validateOrderRequest(CreateOrderRequest request, String sourceIp, String userId) {
        // Check for suspicious patterns
        if (request.getItems().size() > 100) {
            securityMonitoringService.logSecurityEvent(
                "excessive_order_items", sourceIp, userId,
                "Order with " + request.getItems().size() + " items", "medium");
        }

        // Check for potential fraud patterns
        BigDecimal totalValue = request.getItems().stream()
            .map(item -> item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(new BigDecimal("10000")) > 0) {
            securityMonitoringService.logSecurityEvent(
                "high_value_order", sourceIp, userId,
                "Order value: " + totalValue, "medium");
        }
    }

    private double calculateAnomalyScore(CreateOrderRequest request) {
        double score = 0.0;

        // Simple anomaly detection based on order characteristics
        if (request.getItems().size() > 50) {
            score += 0.3;
        }

        BigDecimal totalValue = request.getItems().stream()
            .map(item -> item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(new BigDecimal("5000")) > 0) {
            score += 0.2;
        }

        // Check for duplicate items
        Set<String> productIds = new HashSet<>();
        boolean hasDuplicates = false;
        for (OrderItemRequest item : request.getItems()) {
            if (!productIds.add(item.getProductId())) {
                hasDuplicates = true;
                break;
            }
        }

        if (hasDuplicates) {
            score += 0.2;
        }

        return Math.min(1.0, score);
    }

    private OrderItem convertToOrderItem(OrderItemRequest itemRequest) {
        return OrderItem.builder()
            .productId(itemRequest.getProductId())
            .productName(itemRequest.getProductName())
            .quantity(itemRequest.getQuantity())
            .unitPrice(itemRequest.getUnitPrice())
            .discountAmount(itemRequest.getDiscountAmount())
            .specifications(itemRequest.getSpecifications())
            .build();
    }

    private OrderResponse convertToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .specifications(item.getSpecifications())
                .build())
            .collect(Collectors.toList());

        return OrderResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .customerId(order.getCustomerId())
            .status(order.getStatus())
            .totalAmount(order.getTotalAmount())
            .finalAmount(order.calculateFinalAmount())
            .items(itemResponses)
            .shippingAddress(order.getShippingAddress())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .notes(order.getNotes())
            .tags(order.getTags())
            .build();
    }

    @Cacheable(value = "orders", key = "#orderNumber")
    public Mono<OrderResponse> getOrderByNumber(String orderNumber) {
        return Mono.fromCallable(() -> {
            log.info("Fetching order: {}", orderNumber);

            return orderRepository.findByOrderNumber(orderNumber)
                .map(this::convertToResponse)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderNumber));
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<OrderResponse> getOrdersByCustomer(String customerId) {
        return Flux.fromIterable(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
            .map(this::convertToResponse)
            .subscribeOn(Schedulers.boundedElastic());
    }

    @CacheEvict(value = "orders", key = "#orderNumber")
    @Timed(value = "order.status.update.time", description = "Time taken to update order status")
    public Mono<OrderResponse> updateOrderStatus(String orderNumber, OrderStatusUpdateRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Updating order status: {} to {}", orderNumber, request.getStatus());

            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderNumber));

            OrderStatus previousStatus = order.getStatus();
            order.setStatus(request.getStatus());

            if (request.getNotes() != null) {
                order.setNotes(request.getNotes());
            }

            if (request.getStatus() == OrderStatus.DELIVERED) {
                order.setDeliveredAt(LocalDateTime.now());
            }

            Order updatedOrder = orderRepository.save(order);

            // Publish status change event
            publishOrderStatusChangedEvent(updatedOrder, previousStatus);

            // Update metrics
            if (request.getStatus() == OrderStatus.CANCELLED) {
                orderCancelledCounter.increment();
            }

            log.info("Order status updated successfully: {}", orderNumber);

            return convertToResponse(updatedOrder);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> cancelOrder(String orderNumber, String reason) {
        return updateOrderStatus(orderNumber,
            OrderStatusUpdateRequest.builder()
                .status(OrderStatus.CANCELLED)
                .notes("Cancelled: " + reason)
                .build())
            .then();
    }

    public Flux<OrderResponse> getOrdersByStatus(OrderStatus status) {
        return Flux.fromIterable(orderRepository.findByStatusOrderByCreatedAtDesc(status))
            .map(this::convertToResponse)
            .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<EnhancedOrderAnalytics> getEnhancedOrderAnalytics(
            LocalDateTime startDate, LocalDateTime endDate) {
        return Mono.fromCallable(() -> {
            // Get basic analytics
            List<Order> orders = orderRepository.findOrdersByDateRange(startDate, endDate);

            Long totalOrders = (long) orders.size();
            BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal averageOrderValue = totalOrders > 0 ?
                totalRevenue.divide(new BigDecimal(totalOrders), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

            Map<OrderStatus, Long> statusBreakdown = orders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

            // Get security metrics
            Map<String, Object> securityMetrics = securityMonitoringService.getSecurityMetrics();
            List<SecurityEvent> recentSecurityEvents = securityMonitoringService.getRecentSecurityEvents(10);

            // Calculate anomaly score for the period
            double anomalyScore = calculatePeriodAnomalyScore(orders);

            return EnhancedOrderAnalytics.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(averageOrderValue)
                .statusBreakdown(statusBreakdown)
                .periodStart(startDate)
                .periodEnd(endDate)
                .securityMetrics(securityMetrics)
                .recentSecurityEvents(recentSecurityEvents)
                .anomalyScore(anomalyScore)
                .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private double calculatePeriodAnomalyScore(List<Order> orders) {
        if (orders.isEmpty()) return 0.0;

        // Simple anomaly detection for the period
        double score = 0.0;

        // Check for unusual order volume
        if (orders.size() > 1000) { // Threshold for high volume
            score += 0.3;
        }

        // Check for high-value orders ratio
        long highValueOrders = orders.stream()
            .map(Order::getTotalAmount)
            .filter(amount -> amount.compareTo(new BigDecimal("1000")) > 0)
            .count();

        double highValueRatio = (double) highValueOrders / orders.size();
        if (highValueRatio > 0.3) { // More than 30% high-value orders
            score += 0.2;
        }

        return Math.min(1.0, score);
    }

    private void publishOrderStatusChangedEvent(Order order, OrderStatus previousStatus) {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .previousStatus(previousStatus)
            .newStatus(order.getStatus())
            .timestamp(LocalDateTime.now())
            .build();

        kafkaTemplate.send("order-status-changed", event);
        log.info("Published order status changed event: {} -> {}",
            previousStatus, order.getStatus());
    }
}

// Enhanced Order Controller
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Slf4j
public class EnhancedOrderController {

    private final EnhancedOrderService orderService;
    private final HttpServletRequest request;

    public EnhancedOrderController(EnhancedOrderService orderService, HttpServletRequest request) {
        this.orderService = orderService;
        this.request = request;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Mono<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        String sourceIp = request.getHeader("X-Forwarded-For") != null ?
                         request.getHeader("X-Forwarded-For") : request.getRemoteAddr();
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        log.info("Received create order request for customer: {} from IP: {}",
                request.getCustomerId(), sourceIp);

        return orderService.createOrder(request, sourceIp, userId);
    }

    @GetMapping("/{orderNumber}")
    @PreAuthorize("hasRole('USER')")
    public Mono<OrderResponse> getOrder(@PathVariable String orderNumber) {
        log.info("Received get order request: {}", orderNumber);
        return orderService.getOrderByNumber(orderNumber);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('USER') and #customerId == authentication.name")
    public Flux<OrderResponse> getOrdersByCustomer(@PathVariable String customerId) {
        log.info("Received get orders by customer request: {}", customerId);
        return orderService.getOrdersByCustomer(customerId);
    }

    @PutMapping("/{orderNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<OrderResponse> updateOrderStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        log.info("Received update order status request: {} -> {}", orderNumber, request.getStatus());
        return orderService.updateOrderStatus(orderNumber, request);
    }

    @DeleteMapping("/{orderNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> cancelOrder(
            @PathVariable String orderNumber,
            @RequestParam(defaultValue = "Cancelled by admin") String reason) {
        log.info("Received cancel order request: {}", orderNumber);
        return orderService.cancelOrder(orderNumber, reason);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<OrderResponse> getOrdersByStatus(@PathVariable OrderStatus status) {
        log.info("Received get orders by status request: {}", status);
        return orderService.getOrdersByStatus(status);
    }

    @GetMapping("/enhanced-analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EnhancedOrderAnalytics> getEnhancedOrderAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Received enhanced order analytics request: {} to {}", startDate, endDate);
        return orderService.getEnhancedOrderAnalytics(startDate, endDate);
    }
}
