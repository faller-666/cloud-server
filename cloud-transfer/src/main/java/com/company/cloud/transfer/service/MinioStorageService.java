package com.company.cloud.transfer.service;

import io.minio.*;
import io.minio.messages.Part;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * MinIO multipart 原子能力封装——B 组的底层引擎。
 *
 * 设计要点（对齐任务书 CS-DOC-03 §8）：
 *  - 单分片内存受限：单片 ≤ partSize（默认 8MB），读入 byte[] 后直传，不落盘、不缓存整文件；
 *  - 同一 partNumber 重复上传幂等覆盖（MinIO 原生行为，断点续传/重试基础）；
 *  - 下载只签 5 分钟预签名 URL，字节流走 Nginx → MinIO，不过应用进程。
 *
 * minio-java 9.x：multipart 由 MinioAsyncClient 公开 builder API 提供，方法返回
 * CompletableFuture，封装层统一 await() 转同步并解包异常，供 UploadService 同步调用。
 */
@Service
public class MinioStorageService {

    private final MinioAsyncClient minio;
    private final MinioAsyncClient presignClient;
    private final String bucket;
    private final int partSize;

    public MinioStorageService(MinioAsyncClient minio,
                               @Qualifier("presignClient") MinioAsyncClient presignClient,
                               @Value("${minio.bucket}") String bucket,
                               @Value("${upload.part-size:8388608}") int partSize) {
        this.minio = minio;
        this.presignClient = presignClient;
        this.bucket = bucket;
        this.partSize = partSize;
    }

    public int partSize() {
        return partSize;
    }

    /** 幂等确保 bucket 存在（部署组已建，这里兜底）。 */
    public void ensureBucket() throws Exception {
        boolean found = await(minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
        if (!found) {
            await(minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()));
        }
    }

    /** 初始化 multipart 上传，返回 MinIO uploadId。 */
    public String initMultipart(String objectKey) throws Exception {
        CreateMultipartUploadResponse resp = await(minio.createMultipartUpload(
                CreateMultipartUploadArgs.builder().bucket(bucket).object(objectKey).build()));
        return resp.result().uploadId();
    }

    /** 上传单个分片，返回该片 etag（单片读入内存，≤ partSize）。 */
    public String uploadPart(String objectKey, String uploadId, int partNo,
                             InputStream stream, long size) throws Exception {
        byte[] data = stream.readAllBytes();
        if (size > 0 && data.length != size) {
            throw new IllegalStateException("请求体读取不完整: 声明 " + size + " 字节, 实际读到 " + data.length + " 字节");
        }
        UploadPartResponse resp = await(minio.uploadPart(UploadPartArgs.builder()
                        .bucket(bucket).object(objectKey)
                        .uploadId(uploadId).partNumber(partNo)
                        .data(data, data.length)
                        .build()));
        return resp.part().etag();
    }

    /** 查询已上传分片列表（断点续传：前端跳过已传分片）。 */
    public List<Part> listParts(String objectKey, String uploadId) throws Exception {
        ListPartsArgs args = ListPartsArgs.builder()
                .bucket(bucket).uploadId(uploadId).build();
        setObjectName(args, objectKey);
        return await(minio.listParts(args)).result().parts();
    }

    /**
     * minio-java 9.0.x 缺陷：ListPartsArgs.Builder 误继承 BucketArgs.Builder，
     * 缺少 object() 方法，无法声明对象 key。这里用反射回填 ObjectArgs.objectName。
     */
    private static final Field OBJECT_NAME_FIELD = objectNameField();

    private static Field objectNameField() {
        try {
            Field f = ObjectArgs.class.getDeclaredField("objectName");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("minio ObjectArgs.objectName 字段缺失", e);
        }
    }

    private static void setObjectName(ObjectArgs args, String objectKey) {
        try {
            OBJECT_NAME_FIELD.set(args, objectKey);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法设置 minio objectName", e);
        }
    }

    /** 合并分片。 */
    public void completeMultipart(String objectKey, String uploadId, Part[] parts) throws Exception {
        await(minio.completeMultipartUpload(CompleteMultipartUploadArgs.builder()
                .bucket(bucket).object(objectKey)
                .uploadId(uploadId).parts(parts).build()));
    }

    /** 取消上传，释放 MinIO 暂存空间。 */
    public void abortMultipart(String objectKey, String uploadId) throws Exception {
        await(minio.abortMultipartUpload(AbortMultipartUploadArgs.builder()
                .bucket(bucket).object(objectKey)
                .uploadId(uploadId).build()));
    }

    /** 物理删除对象（引用计数归 0 时调用）。 */
    /** 内容对象的存储 key（objects/<sha256>）；key 规则收口在本模块，跨组调用方不得自行拼串。 */
    public static String objectKeyOf(String sha256) {
        return "objects/" + sha256;
    }

    public void removeObject(String objectKey) throws Exception {
        await(minio.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket).object(objectKey).build()));
    }

    /**
     * 签发下载/预览预签名 URL（默认 5 分钟）。
     * 按文件扩展名补 response-content-type，覆盖 MinIO 因分片上传统一存的
     * application/octet-stream（否则浏览器拒绝渲染图片/PDF/音视频）。
     * inline=true 供预览（Content-Disposition=inline），否则供下载（attachment + 原始文件名）。
     * 使用预签名专用 client，确保 URL 的 host 是浏览器可达的 public-endpoint。
     */
    public String presignGet(String objectKey, String downloadName, int expirySeconds, boolean inline) throws Exception {
        String contentType = contentTypeOf(downloadName);
        String disposition;
        if (inline) {
            disposition = "inline";
        } else {
            String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
            disposition = "attachment; filename=\"" + downloadName.replace("\"", "") + "\"; filename*=UTF-8''" + encoded;
        }
        return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Http.Method.GET)
                .bucket(bucket).object(objectKey)
                .expiry(expirySeconds)
                .extraQueryParams(Map.of(
                        "response-content-type", contentType,
                        "response-content-disposition", disposition))
                .build());
    }

    /** 按文件名扩展名推断 MIME 类型（预览/下载时补正确 Content-Type）。 */
    private static String contentTypeOf(String name) {
        if (name == null || name.isEmpty()) {
            return "application/octet-stream";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "application/octet-stream";
        }
        return switch (name.substring(dot + 1).toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "ico" -> "image/x-icon";
            case "pdf" -> "application/pdf";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "txt", "md", "log" -> "text/plain";
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "xml" -> "application/xml";
            case "zip" -> "application/zip";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }

    /** 转同步并解包 CompletableFuture 异常，让真正的 MinIO 错误原样冒泡。 */
    private static <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}