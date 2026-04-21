package com.uniremington.api.convenia.service;

public interface StorageService {

    /**
     * Uploads bytes to R2 and returns the storage key.
     *
     * @param key         The full R2 object key (path).
     * @param contentType MIME type of the file.
     * @param data        File bytes.
     * @return The key used for storage (same as input key).
     */
    String upload(String key, String contentType, byte[] data);
}
