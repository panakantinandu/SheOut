package com.sheout.sharedkernel.storage;

import com.sheout.sharedkernel.web.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves a stored document to whoever holds a valid signed link - see
 * DocumentLinkSigner for who gets one.
 * <p>
 * A bad signature, an expired link and a key that does not exist all answer
 * the same 404: a caller probing keys learns nothing about which exist.
 */
@RestController
@ConditionalOnProperty(name = "sheout.document-storage.provider", havingValue = "database", matchIfMissing = true)
public class StoredDocumentController {

    private final StoredDocumentRepository documents;
    private final DocumentLinkSigner links;

    public StoredDocumentController(StoredDocumentRepository documents, DocumentLinkSigner links) {
        this.documents = documents;
        this.links = links;
    }

    @GetMapping("/api/v1/documents")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> document(@RequestParam String key, @RequestParam long expires,
                                           @RequestParam String signature) {
        if (!links.isValid(key, expires, signature)) {
            throw ApiException.notFound("No such document");
        }
        StoredDocumentEntity document = documents.findByStorageKey(key)
                .orElseThrow(() -> ApiException.notFound("No such document"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                // Private: a shared cache must never keep somebody's ID document.
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(links.secondsLeft(expires))).cachePrivate())
                .body(document.getContent());
    }
}
