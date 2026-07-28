package com.saleha.promptservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.saleha.promptservice.entity.Prompt;
import com.saleha.promptservice.exception.CloudinaryOperationException;
import com.saleha.promptservice.exception.CloudinaryUnavailableException;
import com.saleha.promptservice.exception.InvalidAttachmentException;
import com.saleha.promptservice.exception.ResourceNotFoundException;
import com.saleha.promptservice.repository.PromptRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    // Reference files this exercise expects: screenshots, sample docs, style guides.
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final Cloudinary cloudinary;
    private final PromptRepository promptRepository;

    public AttachmentService(Cloudinary cloudinary, PromptRepository promptRepository) {
        this.cloudinary = cloudinary;
        this.promptRepository = promptRepository;
    }

    public Prompt uploadAttachment(UUID promptId, MultipartFile file) {

        Prompt prompt = findPrompt(promptId);

        if (file == null || file.isEmpty()) {
            throw new InvalidAttachmentException("No file was provided");
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidAttachmentException("Unsupported file type: " + contentType);
        }

        Map<?, ?> result = uploadToCloudinary(file);

        prompt.setAttachmentUrl((String) result.get("secure_url"));
        prompt.setAttachmentPublicId((String) result.get("public_id"));

        return promptRepository.save(prompt);
    }

    public Prompt deleteAttachment(UUID promptId) {

        Prompt prompt = findPrompt(promptId);

        if (prompt.getAttachmentPublicId() == null) {
            throw new ResourceNotFoundException(
                    "Prompt " + promptId + " has no attachment to remove"
            );
        }

        destroyOnCloudinary(prompt.getAttachmentPublicId());

        prompt.setAttachmentUrl(null);
        prompt.setAttachmentPublicId(null);

        return promptRepository.save(prompt);
    }

    private Prompt findPrompt(UUID promptId) {

        return promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prompt not found with id: " + promptId
                ));
    }

    // Uploads the file, translating raw SDK/IO failures into our own exceptions.
    private Map<?, ?> uploadToCloudinary(MultipartFile file) {

        try {

            return cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("resource_type", "auto")
            );

        } catch (ConnectException | UnknownHostException | SocketTimeoutException e) {

            // Cloudinary itself could not be reached
            throw new CloudinaryUnavailableException("Could not reach Cloudinary", e);

        } catch (IOException e) {

            // Cloudinary was reached but rejected the request
            throw new CloudinaryOperationException(
                    "Cloudinary rejected the upload: " + e.getMessage(), e
            );
        }
    }

    private void destroyOnCloudinary(String publicId) {

        try {

            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

        } catch (ConnectException | UnknownHostException | SocketTimeoutException e) {

            throw new CloudinaryUnavailableException("Could not reach Cloudinary", e);

        } catch (IOException e) {

            throw new CloudinaryOperationException(
                    "Cloudinary rejected the delete: " + e.getMessage(), e
            );
        }
    }
}
