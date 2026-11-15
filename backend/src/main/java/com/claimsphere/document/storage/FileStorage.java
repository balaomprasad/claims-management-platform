package com.claimsphere.document.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Binary storage abstraction. The local-filesystem adapter is used for the assessment; an S3 / Azure Blob adapter
 * can be provided by implementing this interface without touching the document service.
 */
public interface FileStorage {

    /** Stores the content under a server-generated key. */
    void store(String key, InputStream content) throws IOException;

    Resource load(String key) throws IOException;

    void delete(String key) throws IOException;
}
