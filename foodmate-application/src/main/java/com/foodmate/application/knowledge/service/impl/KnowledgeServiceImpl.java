package com.foodmate.application.knowledge.service.impl;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final Set<String> ALLOWED_SOURCE_TYPES =
            Set.of("admin_upload", "internal_sop", "original_recipe", "public_reuse", "policy");
    private static final Pattern EMAIL =
            Pattern.compile(
                    "(?i)(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(?![A-Za-z0-9.-])");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern CHINA_ID =
            Pattern.compile(
                    "(?<!\\d)[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])"
                            + "(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx](?!\\d)");

    private final KnowledgeRepository store;
    private final ObjectStoragePort storage;
    private final IdGenerator ids;
    private final String bucket;

    public KnowledgeServiceImpl(
            ObjectProvider<KnowledgeRepository> store,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<IdGenerator> ids,
            @Value("${foodmate.storage.bucket:foodmate-private}") String bucket) {
        this.store = store.getIfAvailable();
        this.storage = storage.getIfAvailable();
        this.ids = ids.getIfAvailable();
        this.bucket = bucket;
    }

    @Transactional
    public long upload(
            long operatorId,
            String filename,
            String contentType,
            long size,
            InputStream input,
            String traceId) {
        requireAvailable();
        long documentId = ids.nextId();
        String key =
                "knowledge/"
                        + operatorId
                        + "/"
                        + documentId
                        + "-"
                        + filename.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            storage.ensureBucket(bucket);
            storage.put(
                    bucket,
                    key,
                    input,
                    size,
                    contentType == null ? "application/octet-stream" : contentType);
            store.insertDocument(documentId, filename, key, operatorId);
            audit(operatorId, traceId, "knowledge.upload", Long.toString(documentId));
            return documentId;
        } catch (Exception exception) {
            throw new IllegalStateException("knowledge upload failed", exception);
        }
    }

    @Override
    @Transactional
    public long uploadBatch(long operatorId, ImportBatch batch, String traceId) {
        requireAvailable();
        validateBatch(batch);
        KnowledgeRepository.ImportJob existing =
                store.findImportJob(operatorId, batch.idempotencyKey());
        if (existing != null) {
            if (!sameBatch(existing, batch))
                throw new IllegalArgumentException("knowledge import idempotency key conflict");
            return existing.jobId();
        }
        List<ValidatedFile> validatedFiles =
                batch.files().stream()
                        .map(file -> validateFile(file, safeFilename(file.filename())))
                        .toList();
        long jobId = ids.nextId();
        String mode =
                System.getenv().getOrDefault("FOODMATE_RAG_MODE", "stub").toLowerCase(Locale.ROOT);
        if (!mode.equals("stub") && !mode.equals("local"))
            throw new IllegalArgumentException("invalid RAG mode");
        store.insertImportJob(
                new KnowledgeRepository.ImportJob(
                        jobId,
                        operatorId,
                        batch.idempotencyKey(),
                        mode,
                        batch.sourceType(),
                        batch.sourceName(),
                        batch.sourceVersion(),
                        batch.licenseNotice(),
                        traceId));
        List<String> uploadedKeys = new ArrayList<>();
        try {
            storage.ensureBucket(bucket);
            for (int index = 0; index < batch.files().size(); index++) {
                ImportFile file = batch.files().get(index);
                long documentId = ids.nextId();
                long itemId = ids.nextId();
                ValidatedFile validated = validatedFiles.get(index);
                String filename = validated.filename();
                String key = "knowledge/public/" + documentId + "/" + filename;
                storage.put(
                        bucket,
                        key,
                        new ByteArrayInputStream(validated.content()),
                        validated.content().length,
                        validated.contentType());
                uploadedKeys.add(key);
                store.insertDocument(documentId, filename, key, operatorId);
                store.updateDocumentSource(
                        documentId,
                        batch.sourceType(),
                        batch.sourceName(),
                        batch.sourceVersion(),
                        batch.licenseNotice(),
                        operatorId);
                store.insertImportItem(
                        new KnowledgeRepository.ImportItem(
                                itemId,
                                jobId,
                                documentId,
                                filename,
                                validated.contentType(),
                                validated.content().length));
                store.insertIndexOutbox(
                        ids.nextId(),
                        itemId,
                        "{\"job_id\":"
                                + jobId
                                + ",\"item_id\":"
                                + itemId
                                + ",\"document_id\":"
                                + documentId
                                + ",\"version\":\""
                                + jsonString(batch.sourceVersion())
                                + "\",\"mode\":\""
                                + mode
                                + "\"}");
            }
            audit(operatorId, traceId, "knowledge.import_batch.create", Long.toString(jobId));
            return jobId;
        } catch (Exception exception) {
            for (String key : uploadedKeys) {
                try {
                    storage.delete(bucket, key);
                } catch (Exception ignored) {
                    // Prefix reconciliation can remove an object when storage is temporarily down.
                }
            }
            throw new IllegalStateException("knowledge import batch failed", exception);
        }
    }

    @Transactional
    public void updateStatus(
            long documentId, KnowledgeDocumentStatus status, long operatorId, String traceId) {
        requireAvailable();
        if (status == null) throw new IllegalArgumentException("invalid document status");
        if (store.updateStatus(documentId, status, operatorId) != 1)
            throw new IllegalArgumentException("document not found");
        audit(operatorId, traceId, "knowledge.status.update", Long.toString(documentId));
    }

    @Override
    @Transactional
    public void changeVisibility(
            long documentId, String visibility, long operatorId, String traceId) {
        requireAvailable();
        if (!("published".equals(visibility)
                || "disabled".equals(visibility)
                || "deleted".equals(visibility)
                || "draft".equals(visibility)))
            throw new IllegalArgumentException("invalid knowledge visibility");
        KnowledgeRepository.DocumentView document = store.document(documentId);
        if (document == null) throw new IllegalArgumentException("knowledge document not found");
        if (store.updateVisibility(documentId, visibility, operatorId) != 1)
            throw new IllegalArgumentException(
                    "knowledge document is not eligible for visibility change");
        store.insertVisibilityOutbox(
                ids.nextId(),
                documentId,
                "{\"document_id\":"
                        + documentId
                        + ",\"visibility\":\""
                        + visibility
                        + "\",\"tenant_id\":0,\"scope\":\"public_published\",\"version\":\""
                        + jsonString(document.version())
                        + "\",\"current_version\":"
                        + document.currentVersion()
                        + "}");
        audit(operatorId, traceId, "knowledge.visibility." + visibility, Long.toString(documentId));
    }

    @Override
    public BatchDetail batch(long batchId) {
        requireAvailable();
        var job = store.job(batchId);
        if (job == null) throw new IllegalArgumentException("knowledge import batch not found");
        return new BatchDetail(job, store.jobItems(batchId));
    }

    @Override
    public java.util.List<KnowledgeService.BatchEvent> batchEvents(
            long batchId, long afterEventId) {
        batch(batchId);
        return store.jobEvents(batchId, Math.max(0, afterEventId)).stream()
                .map(
                        event ->
                                new KnowledgeService.BatchEvent(
                                        event.eventId(), event.eventType(), event.payload()))
                .toList();
    }

    @Override
    @Transactional
    public void retryItem(long batchId, long documentId, long operatorId, String traceId) {
        BatchDetail detail = batch(batchId);
        KnowledgeRepository.ItemView item =
                detail.items().stream()
                        .filter(value -> value.documentId() == documentId)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "knowledge document is not part of this batch"));
        if (store.retryItem(item.itemId(), batchId, operatorId, ids.nextId(), "{}") != 1)
            throw new IllegalArgumentException("knowledge import item is not retryable");
        audit(operatorId, traceId, "knowledge.import_item.retry", Long.toString(item.documentId()));
    }

    private void audit(long operatorId, String traceId, String action, String documentId) {
        store.insertAudit(
                new KnowledgeRepository.Audit(
                        store.nextAuditId(),
                        operatorId,
                        traceId,
                        "knowledge_document",
                        documentId,
                        action));
    }

    private void requireAvailable() {
        if (store == null || storage == null || ids == null)
            throw new IllegalStateException("knowledge dependencies unavailable");
    }

    private void validateBatch(ImportBatch batch) {
        if (batch == null
                || blank(batch.idempotencyKey())
                || blank(batch.sourceType())
                || blank(batch.sourceName())
                || blank(batch.sourceVersion())
                || blank(batch.licenseNotice())
                || batch.files() == null
                || batch.files().isEmpty()
                || batch.files().size() > 20)
            throw new IllegalArgumentException("KNOWLEDGE_IMPORT_INVALID");
        if (!ALLOWED_SOURCE_TYPES.contains(batch.sourceType().trim().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("KNOWLEDGE_SOURCE_UNAUTHORIZED");
        if (batch.idempotencyKey().length() > 128
                || batch.sourceType().length() > 64
                || batch.sourceName().length() > 255
                || batch.sourceVersion().length() > 128
                || batch.licenseNotice().length() > 1024)
            throw new IllegalArgumentException("KNOWLEDGE_METADATA_INVALID");
        for (ImportFile file : batch.files()) {
            if (file == null
                    || file.input() == null
                    || file.size() <= 0
                    || file.size() > MAX_FILE_SIZE)
                throw new IllegalArgumentException("KNOWLEDGE_FILE_SIZE_INVALID");
            String name = safeFilename(file.filename()).toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".pdf")
                    || name.endsWith(".docx")
                    || name.endsWith(".md")
                    || name.endsWith(".txt")))
                throw new IllegalArgumentException("KNOWLEDGE_FILE_TYPE_UNSUPPORTED");
        }
    }

    private ValidatedFile validateFile(ImportFile file, String filename) {
        byte[] content;
        try (InputStream input = file.input()) {
            content = input.readNBytes((int) MAX_FILE_SIZE + 1);
        } catch (IOException exception) {
            throw new IllegalArgumentException("KNOWLEDGE_FILE_READ_FAILED", exception);
        }
        if (content.length == MAX_FILE_SIZE + 1 || content.length != file.size())
            throw new IllegalArgumentException("KNOWLEDGE_FILE_SIZE_MISMATCH");

        String contentType = normalizeContentType(file.contentType());
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            requireContentType(contentType, "application/pdf");
            requireSignature(content, new byte[] {'%', 'P', 'D', 'F', '-'});
        } else if (lowerName.endsWith(".docx")) {
            requireContentType(
                    contentType,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            requireSignature(content, new byte[] {'P', 'K', 3, 4});
        } else {
            requireContentType(
                    contentType,
                    lowerName.endsWith(".md") ? "text/markdown" : "text/plain",
                    lowerName.endsWith(".md") ? "text/plain" : null);
            String text = new String(content, StandardCharsets.UTF_8);
            if (text.indexOf('\uFFFD') >= 0)
                throw new IllegalArgumentException("KNOWLEDGE_TEXT_ENCODING_INVALID");
            if (containsPersonalData(text))
                throw new IllegalArgumentException("KNOWLEDGE_PII_DETECTED");
        }
        return new ValidatedFile(filename, contentType, content);
    }

    private String normalizeContentType(String contentType) {
        if (blank(contentType)) throw new IllegalArgumentException("KNOWLEDGE_MIME_INVALID");
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void requireContentType(String actual, String... expected) {
        for (String value : expected) if (value != null && value.equals(actual)) return;
        throw new IllegalArgumentException("KNOWLEDGE_MIME_INVALID");
    }

    private void requireSignature(byte[] content, byte[] signature) {
        if (content.length < signature.length)
            throw new IllegalArgumentException("KNOWLEDGE_FILE_SIGNATURE_INVALID");
        for (int index = 0; index < signature.length; index++)
            if (content[index] != signature[index])
                throw new IllegalArgumentException("KNOWLEDGE_FILE_SIGNATURE_INVALID");
    }

    private boolean containsPersonalData(String text) {
        return EMAIL.matcher(text).find()
                || MOBILE.matcher(text).find()
                || CHINA_ID.matcher(text).find();
    }

    private String safeFilename(String filename) {
        if (blank(filename)
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains(".."))
            throw new IllegalArgumentException("invalid knowledge filename");
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean sameBatch(KnowledgeRepository.ImportJob existing, ImportBatch requested) {
        return existing.sourceType().equals(requested.sourceType())
                && existing.sourceName().equals(requested.sourceName())
                && existing.sourceVersion().equals(requested.sourceVersion())
                && existing.licenseNotice().equals(requested.licenseNotice());
    }

    private String jsonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ValidatedFile(String filename, String contentType, byte[] content) {}
}
