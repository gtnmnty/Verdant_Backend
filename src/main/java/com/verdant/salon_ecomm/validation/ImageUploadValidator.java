package com.verdant.salon_ecomm.validation;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// M-25: the one image check every upload path runs before anything is sent to Cloudinary (avatars,
// product/service images, journal photos). It started as MediaImageService.validateFiles, the only path
// that checked properly. Avatars had no check at all (any file up to 10 MB: SVG, HTML, PDF...), and the
// journal trusted the client-supplied Content-Type header.
// Flow per file: not empty -> size limit -> real type from the file's first bytes (Apache Tika, not the
// header or the file name) -> must be on the allow-list.
// SVG is never allowed: it can carry scripts and is served back from a public URL.
// CloudinaryService also passes resource_type=image and the same formats as allowed_formats, so Cloudinary
// rejects anything that gets past this check.
public final class ImageUploadValidator {

    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;

    // Detected MIME types, matching CloudinaryService.ALLOWED_FORMATS (jpg, png, webp, avif).
    // AVIF is kept because the journal already accepted it.
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/avif"
    );

    // Thread-safe; detection only reads the first few KB of the stream.
    private static final Tika TIKA = new Tika();

    private ImageUploadValidator() {
    }

    public static void validateAll(List<MultipartFile> files, long maxBytes) {
        if (files == null || files.isEmpty()) {
            throw BusinessRuleException.invalid("At least one file is required.");
        }
        for (MultipartFile file : files) {
            validate(file, "File '" + file.getOriginalFilename() + "'", maxBytes);
        }
    }

    // label names the file in the error message, e.g. "Avatar" or "Result image".
    public static void validate(MultipartFile file, String label, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw BusinessRuleException.invalid(label + " is empty.");
        }
        if (file.getSize() > maxBytes) {
            throw BusinessRuleException.invalid(label + " is larger than " + (maxBytes / (1024 * 1024)) + " MB.");
        }

        String detectedType;
        try (InputStream in = file.getInputStream()) {
            detectedType = TIKA.detect(in);
        } catch (IOException e) {
            throw BusinessRuleException.invalid("Could not read " + label + ".");
        }
        if (detectedType == null || !ALLOWED_TYPES.contains(detectedType.toLowerCase(Locale.ROOT))) {
            throw BusinessRuleException.invalid(label + " must be a JPEG, PNG, WebP or AVIF image.");
        }
    }
}
