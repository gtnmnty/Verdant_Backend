package com.verdant.salon_ecomm.models.entities;

import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * An uploaded image kept on the owning row (Cloudinary url + publicId for deletion, and the pixel size
 * next/image needs). The owning entity maps the column names with @AttributeOverrides.
 * Wrapper types so an absent optional image is all-NULL columns and loads back as a null object.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoredImage {

    private String url;
    private String publicId;
    private Integer width;
    private Integer height;
}
