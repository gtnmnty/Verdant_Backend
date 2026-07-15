package com.verdant.salon_ecomm.config;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.dtos.reviews.ReviewTargetInfo;
import com.verdant.salon_ecomm.dtos.reviews.ReviewTargetKey;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ReviewBatchLoaderConfig {

    private final ProductRepository productRepository;
    private final SalonServiceRepository salonServiceRepository;

    // BatchLoaderRegistry is an auto-configured Spring GraphQL bean. Registering
    // here, in the constructor, means it's wired once at startup — the loader is
    // then looked up by name ("reviewTargetInfoLoader") in AdminReviewFieldResolver.
    public ReviewBatchLoaderConfig(BatchLoaderRegistry registry,
                                   ProductRepository productRepository,
                                   SalonServiceRepository salonServiceRepository) {
        this.productRepository = productRepository;
        this.salonServiceRepository = salonServiceRepository;

        registry.<ReviewTargetKey, ReviewTargetInfo>forName("reviewTargetInfoLoader")
            .registerMappedBatchLoader((keys, env) -> Mono.just(loadAll(keys)));
    }

    private Map<ReviewTargetKey, ReviewTargetInfo> loadAll(Set<ReviewTargetKey> keys) {
        List<UUID> productIds = keys.stream()
            .filter(k -> k.itemType() == ItemType.PRODUCT)
            .map(ReviewTargetKey::targetId)
            .toList();

        List<UUID> serviceIds = keys.stream()
            .filter(k -> k.itemType() == ItemType.SALON_SERVICE)
            .map(ReviewTargetKey::targetId)
            .toList();

        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        Map<UUID, SalonService> services = salonServiceRepository.findAllById(serviceIds).stream()
            .collect(Collectors.toMap(SalonService::getId, s -> s));

        Map<ReviewTargetKey, ReviewTargetInfo> result = new HashMap<>();

        for (ReviewTargetKey key : keys) {
            if (key.itemType() == ItemType.PRODUCT) {
                Product product = products.get(key.targetId());
                // Non-null fallback: schema declares itemName as String! — a
                // deleted/missing product must not produce a null here.
                String name = product != null ? product.getName() : "[Deleted product]";
                result.put(key, new ReviewTargetInfo(name, null, null));
            } else if (key.itemType() == ItemType.SALON_SERVICE) {
                SalonService service = services.get(key.targetId());
                if (service != null) {
                    AppointmentServiceType type = Boolean.TRUE.equals(service.getIsHomeService())
                        ? AppointmentServiceType.HOME_SERVICE
                        : AppointmentServiceType.IN_SALON;
                    result.put(key, new ReviewTargetInfo(service.getName(), type, service.getName()));
                } else {
                    result.put(key, new ReviewTargetInfo("[Deleted service]", null, null));
                }
            }
        }

        return result;
    }
}