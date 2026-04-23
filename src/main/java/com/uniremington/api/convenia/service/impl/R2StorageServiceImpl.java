package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class R2StorageServiceImpl implements StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public R2StorageServiceImpl(S3Client r2S3Client,
                                @Value("${app.storage.bucket}") String bucket) {
        this.s3Client = r2S3Client;
        this.bucket   = bucket;
    }

    @Override
    public String upload(String key, String contentType, byte[] data) throws S3Exception {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        return key;
    }

    @Override
    public byte[] download(String key) throws S3Exception {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .asByteArray();
    }

    @Override
    public void delete(String key) throws S3Exception {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build());
    }
}
