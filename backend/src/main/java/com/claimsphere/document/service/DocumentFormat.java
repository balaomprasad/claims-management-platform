package com.claimsphere.document.service;

import com.claimsphere.common.exception.BusinessRuleException;

import java.util.Arrays;
import java.util.Optional;

/**
 * Accepted document formats, detected from the file's leading bytes rather than the client-supplied content type
 * or file extension, which are trivially spoofed.
 */
public enum DocumentFormat {
    PDF("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    static final int SIGNATURE_LENGTH = 8;

    private final String contentType;
    private final byte[] signature;

    DocumentFormat(String contentType, byte[] signature) {
        this.contentType = contentType;
        this.signature = signature;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<DocumentFormat> detect(byte[] header) {
        return Arrays.stream(values())
                .filter(format -> header.length >= format.signature.length
                        && Arrays.equals(header, 0, format.signature.length, format.signature, 0,
                        format.signature.length))
                .findFirst();
    }

    public static DocumentFormat require(byte[] header) {
        return detect(header).orElseThrow(() -> new BusinessRuleException("UNSUPPORTED_DOCUMENT_TYPE",
                "Only PDF, PNG and JPEG documents are accepted"));
    }
}
