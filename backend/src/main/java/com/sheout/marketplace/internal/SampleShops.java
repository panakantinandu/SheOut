package com.sheout.marketplace.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.marketplace.ProductTerms;
import com.sheout.marketplace.SellerCategory;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Example shops for SheOut Seller while real sellers are still signing up,
 * so the directory shows what it is for instead of one lonely product.
 * <p>
 * Honest by construction: each is flagged {@code sample}, the app labels it
 * "Sample shop", and no phone or WhatsApp number is ever sent for it - there
 * is nobody to reach. On with MARKETPLACE_SAMPLE_SHOPS=true; turning it off
 * removes every sample shop, its products and photos at the next start.
 * <p>
 * The shops are in sample-shops/catalog.json, each photo beside it. Each
 * shop's account id is derived from its key, so a restart finds the shops it
 * made and adds only what is missing. Sample shops belong to no real account.
 */
@Component
class SampleShops {

    private static final Logger log = LoggerFactory.getLogger(SampleShops.class);
    private static final String CATALOG = "sample-shops/catalog.json";
    /** Never shown: a sample shop's numbers are withheld. The column needs a value. */
    private static final String NO_NUMBER = "0000000000";

    private final SellerProfileRepository sellers;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final DocumentStorage storage;
    private final ClusterLock lock;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final boolean enabled;

    SampleShops(SellerProfileRepository sellers, ProductRepository products, ProductImageRepository images,
                DocumentStorage storage, ClusterLock lock, ObjectMapper json, PlatformTransactionManager transactions,
                @Value("${sheout.marketplace.sample-shops:false}") boolean enabled) {
        this.sellers = sellers;
        this.products = products;
        this.images = images;
        this.storage = storage;
        this.lock = lock;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        try {
            lock.runExclusively("sample-shops", Duration.ofMinutes(5), enabled ? this::ensure : this::removeAll);
        } catch (RuntimeException ex) {
            // Examples are never worth a server that will not start.
            log.error("Sample shops could not be {}: {}", enabled ? "added" : "removed", ex.toString());
        }
    }

    /** Adds every catalogue shop that is not there yet. Package-visible for tests. */
    int ensure() {
        JsonNode catalog;
        try (InputStream in = new ClassPathResource(CATALOG).getInputStream()) {
            catalog = json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + CATALOG, e);
        }
        int added = 0;
        for (JsonNode shop : catalog.path("shops")) {
            UUID account = accountFor(shop.path("key").asText());
            if (sellers.findByAccountId(account).isPresent()) {
                continue;
            }
            tx.executeWithoutResult(status -> addShop(account, shop));
            added++;
        }
        if (added > 0) {
            log.info("Added {} sample shops to SheOut Seller", added);
        }
        return added;
    }

    private void addShop(UUID account, JsonNode shop) {
        SellerCategory category = SellerCategory.valueOf(shop.path("category").asText());
        String custom = category == SellerCategory.OTHER ? shop.path("customCategory").asText() : null;
        SellerProfileEntity seller = new SellerProfileEntity(account, shop.path("name").asText(), category, custom,
                NO_NUMBER, null, shop.path("area").asText(), null);
        seller.makeSample(Instant.now());
        seller = sellers.save(seller);
        for (JsonNode p : shop.path("products")) {
            ProductEntity product = new ProductEntity(seller.getId(), freshCode(), p.path("title").asText(),
                    p.path("description").asText(), new BigDecimal(p.path("price").asText()),
                    p.hasNonNull("originalPrice") ? new BigDecimal(p.path("originalPrice").asText()) : null, true);
            product.setTerms(ProductTerms.defaults());
            product = products.save(product);
            String photo = "sample-shops/" + shop.path("key").asText() + "--" + p.path("key").asText() + ".jpg";
            try (InputStream in = new ClassPathResource(photo).getInputStream()) {
                String key = storage.store(account, "product-photo",
                        new DocumentUpload(photo.substring(photo.lastIndexOf('/') + 1), "image/jpeg", in.readAllBytes()));
                images.save(new ProductImageEntity(product.getId(), seller.getId(), key, 1));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + photo, e);
            }
        }
    }

    /** Removes every sample shop with its products and photos. Package-visible for tests. */
    int removeAll() {
        List<SellerProfileEntity> samples = sellers.findBySampleTrue();
        for (SellerProfileEntity seller : samples) {
            tx.executeWithoutResult(status -> {
                for (ProductImageEntity image : images.findBySellerId(seller.getId())) {
                    try {
                        storage.delete(image.getStorageKey());
                    } catch (RuntimeException e) {
                        log.warn("Could not delete sample photo {}", image.getStorageKey());
                    }
                }
                images.deleteAll(images.findBySellerId(seller.getId()));
                products.deleteAll(products.findBySellerIdOrderByCreatedAtAsc(seller.getId()));
                sellers.delete(seller);
            });
        }
        if (!samples.isEmpty()) {
            log.info("Removed {} sample shops from SheOut Seller", samples.size());
        }
        return samples.size();
    }

    private String freshCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = ProductCodes.next();
            if (!products.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not draw an unused product code");
    }

    static UUID accountFor(String shopKey) {
        return UUID.nameUUIDFromBytes(("sheout-sample-shop:" + shopKey).getBytes(StandardCharsets.UTF_8));
    }
}
