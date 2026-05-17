package com.shasank.cms_form_extractor.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class FileProcessingUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileProcessingUtil.class);

    /**
     * Convert PDF to images (one image per page)
     */
    public List<BufferedImage> convertPdfToImages(MultipartFile file) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        byte[] fileContent = file.getBytes();

        try (PDDocument document = Loader.loadPDF(fileContent)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300); // 300 DPI
                images.add(image);
                logger.info("Converted PDF page {} to image", page + 1);
            }
        }

        return images;
    }

    /**
     * Load image from multipart file
     */
    public BufferedImage loadImage(MultipartFile file) throws IOException {
        return ImageIO.read(file.getInputStream());
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
