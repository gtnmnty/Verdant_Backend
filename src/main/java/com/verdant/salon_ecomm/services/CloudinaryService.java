package com.verdant.salon_ecomm.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryUploadResult upload(MultipartFile file) {
        try {
            Map result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
            return new CloudinaryUploadResult(
                (String) result.get("secure_url"),
                (String) result.get("public_id")
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }

    public void delete(String publicId) {
        try {
            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
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
}