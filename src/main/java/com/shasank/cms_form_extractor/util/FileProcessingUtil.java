package com.shasank.cms_form_extractor.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileProcessingUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileProcessingUtil.class);
    private static final int MAX_IMAGE_WIDTH = 1024;
    private static final int MAX_IMAGE_HEIGHT = 1024;
    private static final int VISION_ALIGNMENT = 28;

    /**
     * Convert PDF to images (one image per page)
     */
    public List<BufferedImage> convertPdfToImages(MultipartFile file) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        byte[] fileContent = file.getBytes();

        try (PDDocument document = Loader.loadPDF(fileContent)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                BufferedImage scaled = downscaleImage(image);
                images.add(scaled);
                logger.info("Converted PDF page {} to image", page + 1);
            }
        }

        return images;
    }

    /**
     * Load image from multipart file
     */
    public BufferedImage loadImage(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        return downscaleImage(image);
    }

    /**
     * Downscale image to prevent GGML tensor errors in vision models
     */
    public BufferedImage downscaleImage(BufferedImage image) {
        if (image == null) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            logger.debug("Image size OK: {}x{}", width, height);
            return image;
        }

        double scale = Math.min((double) MAX_IMAGE_WIDTH / width, (double) MAX_IMAGE_HEIGHT / height);
        int newWidth = normalizeDimension((int) (width * scale));
        int newHeight = normalizeDimension((int) (height * scale));

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        logger.info("Downscaled image from {}x{} to {}x{}", width, height, newWidth, newHeight);
        return scaled;
    }

    private int normalizeDimension(int value) {
        if (value < VISION_ALIGNMENT) {
            return VISION_ALIGNMENT;
        }
        int aligned = (value / VISION_ALIGNMENT) * VISION_ALIGNMENT;
        return Math.max(aligned, VISION_ALIGNMENT);
    }

    /**
     * Extract metadata from filename (e.g., membership ID, dates, provider info)
     */
    public Map<String, String> extractMetadataFromFilename(String filename) {
        Map<String, String> metadata = new HashMap<>();

        if (filename == null || filename.isBlank()) {
            return metadata;
        }

        // Remove file extension
        String nameWithoutExt = filename.replaceAll("\\.[^.]*$", "");

        // Try to extract membership ID (pattern: Numbers with hyphens, typically after date)
        Pattern membershipPattern = Pattern.compile("([0-9]+-[0-9]+(?:-[0-9A-Z]+)?)");
        Matcher membershipMatcher = membershipPattern.matcher(nameWithoutExt);
        if (membershipMatcher.find()) {
            metadata.put("membershipId", membershipMatcher.group(1));
            logger.debug("Extracted membership ID: {}", metadata.get("membershipId"));
        }

        // Try to extract date (pattern: YYYYMMDD or YYYY-MM-DD)
        Pattern datePattern = Pattern.compile("(\\d{4}[_-]?\\d{2}[_-]?\\d{2})");
        Matcher dateMatcher = datePattern.matcher(nameWithoutExt);
        if (dateMatcher.find()) {
            metadata.put("claimDate", dateMatcher.group(1).replaceAll("[_-]", ""));
            logger.debug("Extracted claim date: {}", metadata.get("claimDate"));
        }

        // Try to extract NPI (10 digits grouped)
        Pattern npiPattern = Pattern.compile("\\b(\\d{10})\\b");
        Matcher npiMatcher = npiPattern.matcher(nameWithoutExt);
        if (npiMatcher.find()) {
            metadata.put("providerNPI", npiMatcher.group(1));
            logger.debug("Extracted provider NPI: {}", metadata.get("providerNPI"));
        }

        logger.info("Extracted metadata from filename [{}]: {}", filename, metadata);
        return metadata;
    }

    /**
     * Convert BufferedImage to base64 string for Ollama API
     */
    public String imageToBase64(BufferedImage image) throws IOException {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        return java.util.Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Get file type from MultipartFile
     */
    public String getFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.contains("pdf")) {
                return "pdf";
            } else if (contentType.contains("image")) {
                return "image";
            }
        }

        // Fallback to file extension check
        String filename = file.getOriginalFilename();
        if (filename != null) {
            if (filename.toLowerCase().endsWith(".pdf")) {
                return "pdf";
            } else if (filename.toLowerCase().matches(".*\\.(jpg|jpeg|png|bmp|gif)$")) {
                return "image";
            }
        }

        return "unknown";
    }
}
