package com.verdant.salon_ecomm.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED_URL_HOSTS = List.of(
        "res.cloudinary.com",
        "lh3.googleusercontent.com"   // Google OAuth profile pictures
    );

    // M-25: second line of defence behind ImageUploadValidator. resource_type=image makes Cloudinary treat
    // the upload as an image (no raw/video files), and allowed_formats makes it reject any other format,
    // so even a caller that skipped the validator can't store an SVG, HTML or PDF under our account.
    // Same list as ImageUploadValidator.ALLOWED_TYPES.
    static final String ALLOWED_FORMATS = "jpg,png,webp,avif";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> imageUploadOptions() {
        return ObjectUtils.asMap(
            "resource_type", "image",
            "allowed_formats", ALLOWED_FORMATS
        );
    }


    @SuppressWarnings("unchecked")
    public CloudinaryUploadResult upload(MultipartFile file) {
        try {
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), imageUploadOptions());
            return new CloudinaryUploadResult(
                (String) result.get("secure_url"),
                (String) result.get("public_id")
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }

    @SuppressWarnings("unchecked")
    public CloudinaryUploadResult uploadFromUrl(String sourceUrl) {
        // Validate the URL host before passing to Cloudinary
        try {
            java.net.URI uri = java.net.URI.create(sourceUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("URL must use https: " + sourceUrl);
            }
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("URL has no host: " + sourceUrl);
            }
            String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
            // Exact host or a real subdomain: "evilres.cloudinary.com" must not pass for "res.cloudinary.com".
            boolean allowed = ALLOWED_URL_HOSTS.stream()
                .anyMatch(h -> normalizedHost.equals(h) || normalizedHost.endsWith("." + h));
            if (!allowed) {
                throw new IllegalArgumentException("URL host not in allow-list: " + host);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid or disallowed source URL: " + sourceUrl, e);
        }
        try {
            Map<String, Object> result = cloudinary.uploader().upload(sourceUrl, imageUploadOptions());
            return new CloudinaryUploadResult(
                (String) result.get("secure_url"),
                (String) result.get("public_id")
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy image in Cloudinary", e);
        }
    }

    // Same upload as upload(), but also returns the pixel size Cloudinary reports (used by the Journal's
    // masonry grid). Existing upload() is untouched so avatar/product code keeps working.
    @SuppressWarnings("unchecked")
    public CloudinaryImageResult uploadWithDimensions(MultipartFile file) {
        try {
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), imageUploadOptions());
            return new CloudinaryImageResult(
                (String) result.get("secure_url"),
                (String) result.get("public_id"),
                result.get("width") instanceof Number w ? w.intValue() : 0,
                result.get("height") instanceof Number h ? h.intValue() : 0
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void delete(String publicId) {
        try {
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String status = (String) result.get("result");

            if (!"ok".equals(status) && !"not found".equals(status)) {
                throw new RuntimeException("Cloudinary deletion failed: " + status);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete image from Cloudinary: " + publicId, e);
        }
    }

    public record CloudinaryUploadResult(String url, String publicId) {
    }

    public record CloudinaryImageResult(String url, String publicId, int width, int height) {
    }
}