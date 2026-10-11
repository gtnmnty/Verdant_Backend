package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.journal.JournalImageDto;
import com.verdant.salon_ecomm.dtos.journal.JournalProductDto;
import com.verdant.salon_ecomm.dtos.journal.JournalStoryDto;
import com.verdant.salon_ecomm.dtos.journal.admin.AdminJournalStoryDto;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.StoredImage;
import com.verdant.salon_ecomm.models.entities.journal.JournalStory;
import org.springframework.stereotype.Component;

import java.util.List;


//Pure mapping, no repository calls. The service loads each story's products (and their images) in bulk
//and passes the finished JournalProductDto list in, so mapping a page of stories costs no extra queries.
@Component
public class JournalMapper {

    public JournalImageDto toImageDto(StoredImage image) {
        if (image == null || image.getUrl() == null) return null;
        return new JournalImageDto(
            image.getUrl(),
            image.getWidth() != null ? image.getWidth() : 0,
            image.getHeight() != null ? image.getHeight() : 0
        );
    }

    // inStock follows the same rule as ProductStockResolver (stock quantity above zero).
    public JournalProductDto toProductDto(Product product, String imageUrl) {
        return new JournalProductDto(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getSalePrice(),
            imageUrl,
            product.getStockQuantity() != null && product.getStockQuantity() > 0
        );
    }

    public JournalStoryDto toPublicDto(JournalStory story, List<JournalProductDto> products) {
        return new JournalStoryDto(
            story.getId(),
            story.getClientName(),
            story.getQuote(),
            story.getCategory(),
            story.getService(),
            story.getStylist(),
            toImageDto(story.getResultImage()),
            toImageDto(story.getProductImage()),
            products,
            story.getPublishedAt()
        );
    }

    public AdminJournalStoryDto toAdminDto(JournalStory story, List<JournalProductDto> products) {
        return new AdminJournalStoryDto(
            story.getId(),
            story.getClientName(),
            story.getQuote(),
            story.getCategory(),
            story.getService(),
            story.getStylist(),
            story.getStatus(),
            toImageDto(story.getResultImage()),
            toImageDto(story.getProductImage()),
            products,
            story.getPublishedAt(),
            story.getCreatedAt(),
            story.getUpdatedAt()
        );
    }
}
