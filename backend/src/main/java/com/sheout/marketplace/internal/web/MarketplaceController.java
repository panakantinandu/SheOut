package com.sheout.marketplace.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.marketplace.MarketplaceError;
import com.sheout.marketplace.MarketplaceViews.DirectoryFilter;
import com.sheout.marketplace.MarketplaceViews.ListingCard;
import com.sheout.marketplace.MarketplaceViews.ProductDetail;
import com.sheout.marketplace.MarketplaceViews.ProductDetails;
import com.sheout.marketplace.MarketplaceViews.SellerDetails;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.marketplace.ProductApi;
import com.sheout.marketplace.SellerApi;
import com.sheout.marketplace.SellerCategory;
import com.sheout.payments.ListingFeeCheckout;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SheOut Seller, for riders: the directory, and her own shop.
 * <p>
 * The directory is deliberately public to every signed-in rider - it is a
 * listing, like a shop's signboard, not anybody's private record - so it is
 * gated by role alone. The enumeration-safe 404 used for bookings and
 * payments is for things that belong to one person, which a listing does
 * not. A product that is not in the directory is still simply 404.
 * <p>
 * Everything under /seller/me acts on the caller's own shop, found from her
 * token; there is no seller id to change in a request.
 */
@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceController {

    private final SellerApi sellers;
    private final ProductApi products;

    public MarketplaceController(SellerApi sellers, ProductApi products) {
        this.sellers = sellers;
        this.products = products;
    }

    // ------------------------------------------------------------ the directory

    /**
     * The directory, narrowed. category may repeat (?category=MEHANDI&category=GIFTS)
     * for any of several; minPrice and maxPrice are inclusive rupee bounds on
     * the display price; area matches the seller's own area text.
     */
    @GetMapping("/listings")
    public ResponseEntity<PageResponse<ListingCard>> listings(
            @RequestParam(required = false) List<SellerCategory> category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        requireCustomer();
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_RANGE", "Prices can't be below zero.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_RANGE", "The lowest price is above the highest.");
        }
        DirectoryFilter filter = new DirectoryFilter(
                category == null ? Set.of() : Set.copyOf(category),
                clip(q), minPrice, maxPrice, clip(area));
        PageRequest pageable = PageRequest.of(PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize));
        return ResponseEntity.ok(PageResponse.from(products.browseListings(filter, pageable), card -> card));
    }

    /** A search term as far as it is worth matching: 80 characters is longer than any shop name or area. */
    private static String clip(String text) {
        return text != null && text.length() > 80 ? text.substring(0, 80) : text;
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDetail> product(@PathVariable UUID productId) {
        requireCustomer();
        return products.getProductDetail(productId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                        "This listing is no longer available."));
    }

    // ------------------------------------------------------------ her shop

    @GetMapping("/seller/me")
    public ResponseEntity<SellerView> mine() {
        CurrentAccount caller = requireCustomer();
        return sellers.mySeller(caller.accountId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> toApiException(MarketplaceError.NOT_A_SELLER));
    }

    @PostMapping("/seller/me")
    public ResponseEntity<SellerView> apply(@Valid @RequestBody SellerRequest request) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.status(HttpStatus.CREATED).body(unwrap(sellers.applyAsSeller(caller.accountId(), request.details())));
    }

    @PutMapping("/seller/me")
    public ResponseEntity<SellerView> update(@Valid @RequestBody SellerRequest request) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(sellers.updateProfile(caller.accountId(), request.details())));
    }

    @PostMapping("/seller/me/submit")
    public ResponseEntity<SellerView> submit() {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(sellers.submitForReview(caller.accountId())));
    }

    @PostMapping("/seller/me/products")
    public ResponseEntity<SellerView> addProduct(@Valid @RequestBody ProductRequest request) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.status(HttpStatus.CREATED).body(unwrap(products.addProduct(caller.accountId(), request.details())));
    }

    @PutMapping("/seller/me/products/{productId}")
    public ResponseEntity<SellerView> updateProduct(@PathVariable UUID productId, @Valid @RequestBody ProductRequest request) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(products.updateProduct(caller.accountId(), productId, request.details())));
    }

    @DeleteMapping("/seller/me/products/{productId}")
    public ResponseEntity<SellerView> deleteProduct(@PathVariable UUID productId) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(products.deleteProduct(caller.accountId(), productId)));
    }

    @PostMapping(value = "/seller/me/products/{productId}/images", consumes = "multipart/form-data")
    public ResponseEntity<SellerView> addImage(@PathVariable UUID productId, @RequestParam("file") MultipartFile file) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(products.addProductImage(caller.accountId(), productId, ProductPhotoUploads.toUpload(file))));
    }

    @DeleteMapping("/seller/me/products/{productId}/images/{imageId}")
    public ResponseEntity<SellerView> deleteImage(@PathVariable UUID productId, @PathVariable UUID imageId) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(products.deleteProductImage(caller.accountId(), productId, imageId)));
    }

    // ------------------------------------------------------------ the listing fee

    @PostMapping("/seller/me/listing-fee/checkout")
    public ResponseEntity<ListingFeeCheckout> startFee() {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(sellers.startListingFeePayment(caller.accountId())));
    }

    /** The fee from her SheOut wallet - she goes live at once. 409 INSUFFICIENT_BALANCE when it is short. */
    @PostMapping("/seller/me/listing-fee/wallet")
    public ResponseEntity<SellerView> payFeeFromWallet() {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(sellers.payListingFeeFromWallet(caller.accountId())));
    }

    @PostMapping("/seller/me/listing-fee/confirm")
    public ResponseEntity<SellerView> confirmFee(@Valid @RequestBody ConfirmFeeRequest request) {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(unwrap(sellers.confirmListingFeePayment(caller.accountId(),
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())));
    }

    // ------------------------------------------------------------ requests

    public record SellerRequest(
            @NotBlank @Size(max = 80) String businessName,
            @NotNull SellerCategory category,
            @NotBlank @Size(max = 20) String contactPhone,
            @Size(max = 20) String whatsappNumber,
            @Size(max = 80) String area) {
        SellerDetails details() {
            return new SellerDetails(businessName, category, contactPhone, whatsappNumber, area);
        }
    }

    public record ProductRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal displayPrice,
            Boolean active) {
        ProductDetails details() {
            return new ProductDetails(title, description, displayPrice, active == null || active);
        }
    }

    public record ConfirmFeeRequest(@NotBlank String razorpayOrderId, @NotBlank String razorpayPaymentId,
                                    @NotBlank String razorpaySignature) {
    }

    // ------------------------------------------------------------ helpers

    private static CurrentAccount requireCustomer() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER) {
            throw ApiException.forbidden("SheOut Seller is in the rider app");
        }
        return caller;
    }

    private static <T> T unwrap(Result<T, MarketplaceError> result) {
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return result.value();
    }

    static ApiException toApiException(MarketplaceError error) {
        return switch (error) {
            case NOT_A_SELLER -> new ApiException(HttpStatus.NOT_FOUND, error.name(), "You have not applied to sell on SheOut yet.");
            case ALREADY_A_SELLER -> new ApiException(HttpStatus.CONFLICT, error.name(), "You already have a SheOut Seller shop.");
            case INVALID_PHONE -> new ApiException(HttpStatus.BAD_REQUEST, error.name(), "Enter a 10-digit Indian mobile number.");
            case NOT_EDITABLE -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "Your shop can't be changed while it is being reviewed, awaiting payment or suspended.");
            case NOT_SUBMITTABLE -> new ApiException(HttpStatus.CONFLICT, error.name(), "Your shop has already been sent for review.");
            case NOTHING_TO_REVIEW -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "Add at least one product with a photo before sending your shop for review.");
            case NOT_VERIFIED -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "Verify your ID first. Every SheOut Seller is a verified SheOut rider.");
            case PRODUCT_NOT_FOUND -> new ApiException(HttpStatus.NOT_FOUND, error.name(), "That product was not found.");
            case IMAGE_NOT_FOUND -> new ApiException(HttpStatus.NOT_FOUND, error.name(), "That photo was not found.");
            case PRODUCT_LIMIT_REACHED -> new ApiException(HttpStatus.CONFLICT, error.name(), "You have reached the most products a shop can list.");
            case PRODUCT_IMAGE_LIMIT_REACHED -> new ApiException(HttpStatus.CONFLICT, error.name(), "This product already has the most photos it can have.");
            case SELLER_IMAGE_LIMIT_REACHED -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "Your shop has used all its photos. Remove one to add another.");
            case IMAGE_STORAGE_FAILED -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, error.name(), "The photo could not be saved. Please try again.");
            case NOT_AWAITING_PAYMENT -> new ApiException(HttpStatus.CONFLICT, error.name(), "The listing fee is due only once your shop is approved.");
            case ALREADY_PAID -> new ApiException(HttpStatus.CONFLICT, error.name(), "Your listing fee is already paid.");
            case INSUFFICIENT_BALANCE -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "Your SheOut wallet does not have enough for the listing fee. Add money or pay online.");
            case PAYMENT_FAILED -> new ApiException(HttpStatus.BAD_GATEWAY, error.name(), "The payment could not be started or confirmed. Please try again.");
            case PAYMENT_NOT_VERIFIED -> new ApiException(HttpStatus.BAD_REQUEST, error.name(), "The payment could not be verified.");
            case SELLER_NOT_FOUND -> new ApiException(HttpStatus.NOT_FOUND, error.name(), "No such seller.");
            case INVALID_TRANSITION -> new ApiException(HttpStatus.CONFLICT, error.name(), "That can't be done to this seller in their current state.");
            case REASON_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, error.name(), "A reason is required.");
        };
    }
}
