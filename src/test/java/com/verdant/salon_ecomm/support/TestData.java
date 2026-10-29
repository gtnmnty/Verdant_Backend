package com.verdant.salon_ecomm.support;

import com.verdant.salon_ecomm.models.entities.CartItem;
import com.verdant.salon_ecomm.models.entities.MediaImage;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.SalonService;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.repositories.CartItemRepository;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

// Seeds real rows for integration tests (imported by AbstractIntegrationTest). Every value that must be
// unique gets a random suffix, because test classes share one database for the whole run.
public class TestData {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final CartItemRepository cartItemRepository;
    private final MediaImageRepository mediaImageRepository;
    private final OrderRepository orderRepository;

    public TestData(UserRepository userRepository, ProductRepository productRepository,
                    SalonServiceRepository salonServiceRepository, CartItemRepository cartItemRepository,
                    MediaImageRepository mediaImageRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.salonServiceRepository = salonServiceRepository;
        this.cartItemRepository = cartItemRepository;
        this.mediaImageRepository = mediaImageRepository;
        this.orderRepository = orderRepository;
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.verdant.local";
    }

    public User customer() {
        return user(AccountRole.CUSTOMER, uniqueEmail());
    }

    public User user(AccountRole role, String email) {
        return userRepository.saveAndFlush(User.builder()
            .fullName("Test " + role.name().toLowerCase())
            .email(email)
            .passwordHash("{noop}not-used")
            .role(role)
            .status(AccountStatus.ACTIVE)
            .emailVerified(true)
            .enabled(true)
            .build());
    }

    public Product product(int stock) {
        return productRepository.saveAndFlush(Product.builder()
            .name("Test product " + UUID.randomUUID())
            .itemCatalog(ItemCatalog.SKIN_CARE)
            .price(new BigDecimal("25.00"))
            .stockQuantity(stock)
            .status(CollectionStatus.ACTIVE)
            .build());
    }

    public SalonService salonService() {
        return salonServiceRepository.saveAndFlush(SalonService.builder()
            .name("Test service " + UUID.randomUUID())
            .subName("Test")
            .itemCatalog(ItemCatalog.HAIR_CARE)
            .durationMinutes(60)
            .price(new BigDecimal("80.00"))
            .status(CollectionStatus.ACTIVE)
            .build());
    }

    public CartItem cartItem(User user, Product product, int quantity) {
        return cartItemRepository.saveAndFlush(CartItem.builder()
            .user(user).product(product).quantity(quantity).deliveryOption(DeliveryOption.STANDARD).build());
    }

    public MediaImage image(ItemType type, UUID entityId, boolean primary, int sortOrder) {
        return mediaImageRepository.saveAndFlush(MediaImage.builder()
            .entityType(type).entityId(entityId)
            .url("https://img.test/" + UUID.randomUUID() + ".jpg").publicId("test/" + UUID.randomUUID())
            .isPrimary(primary).sortOrder(sortOrder).build());
    }

    // An unpaid order that already has a Stripe PaymentIntent, as createPaymentIntent leaves it.
    public Order orderAwaitingPayment(User user, String paymentIntentId) {
        return orderRepository.saveAndFlush(Order.builder()
            .user(user)
            .orderStatus(OrderStatus.PLACED)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod("card")
            .subtotal(new BigDecimal("100.00"))
            .total(new BigDecimal("100.00"))
            .stripePaymentIntentId(paymentIntentId)
            .build());
    }

    // For @PreAuthorize service methods called from test threads (the security context is per thread).
    public static void signInAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
