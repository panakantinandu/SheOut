package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceError;
import com.sheout.marketplace.MarketplaceViews.ListingCard;
import com.sheout.marketplace.MarketplaceViews.ProductDetail;
import com.sheout.marketplace.MarketplaceViews.ProductDetails;
import com.sheout.marketplace.MarketplaceViews.DirectoryFilter;
import com.sheout.marketplace.MarketplaceViews.SellerDetails;
import com.sheout.marketplace.MarketplaceViews.SmartSearchResult;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.marketplace.SellerCategory;
import com.sheout.marketplace.SellerStatus;
import com.sheout.marketplace.SellerStatusChanged;
import com.sheout.payments.ListingFeeCheckout;
import com.sheout.payments.ListingFeePaid;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentPurpose;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.PaymentSummary;
import com.sheout.payments.internal.PaymentService;
import com.sheout.payments.internal.gateway.GatewayOrder;
import com.sheout.payments.internal.gateway.GatewayPayment;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.CustomerProfileSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SheOut Seller end to end against the real database: the migration, the
 * directory's queries, the photo caps, and the listing fee going through
 * payments' own order, webhook and Checkout paths. Only the two things that
 * leave the building are stood in for - Razorpay, and the ID check a rider
 * passes by sending a document to a person.
 * <p>
 * Like SheOutApplicationTests, this needs the local Postgres and Redis.
 */
@SpringBootTest(properties = {
        "sheout.warm-up.enabled=false",
        "sheout.marketplace.listing-fee=299",
        "sheout.marketplace.max-images-per-product=2",
        "sheout.marketplace.max-images-per-seller=3",
        // Its own context (the mocks make it one), on a local Postgres with
        // 20 connections: a small pool, closed when the class is done.
        "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@RecordApplicationEvents
@DirtiesContext
class MarketplaceFlowTest {

    @Autowired SellerService sellers;
    @Autowired ProductService products;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents events;

    @MockBean PaymentGateway gateway;
    /** A spy, not a mock: the same bean also serves emergency contacts, which SOS needs to start. */
    @SpyBean CustomerProfileApi customerProfiles;

    private final UUID asha = UUID.randomUUID();
    private final UUID bina = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final List<UUID> accounts = new ArrayList<>(List.of(asha, bina));
    /** Unique per run, so the directory searches find this run's shops and no one else's. */
    private final String tag = "t" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void setUp() {
        CustomerProfileSummary verified = mock(CustomerProfileSummary.class);
        when(verified.verified()).thenReturn(true);
        doReturn(Optional.of(verified)).when(customerProfiles).findByAccountId(any());
        when(gateway.publicKeyId()).thenReturn("rzp_test_key");
        when(gateway.createOrder(any(), any())).thenAnswer(inv -> Result.success(new GatewayOrder("order_" + UUID.randomUUID())));
        when(gateway.verifyCheckoutSignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(gateway.confirmCapture(anyString(), anyString(), any()))
                .thenAnswer(inv -> Result.success(new GatewayPayment(inv.getArgument(0), PaymentMethod.CARD)));
    }

    @AfterEach
    void cleanUp() {
        for (UUID account : accounts) {
            jdbc.update("delete from rider_wallet_entries where customer_account_id = ?", account);
            jdbc.update("delete from rider_wallets where customer_account_id = ?", account);
            jdbc.update("delete from payments where payer_account_id = ?", account);
            jdbc.update("delete from seller_product_images where seller_id in (select id from seller_profiles where account_id = ?)", account);
            jdbc.update("delete from seller_products where seller_id in (select id from seller_profiles where account_id = ?)", account);
            jdbc.update("delete from seller_profiles where account_id = ?", account);
        }
    }

    @Test
    void aShopGoesFromDraftToTheDirectoryOnlyOnceReviewedAndPaid() throws IOException {
        // ---- her application
        assertThat(sellers.applyAsSeller(asha, details("Asha " + tag, "98765 00001", "abc")).error())
                .isEqualTo(MarketplaceError.INVALID_PHONE);
        SellerView draft = ok(sellers.applyAsSeller(asha, new SellerDetails("Asha " + tag + " Sarees", SellerCategory.FASHION_SAREE,
                "+91 98765 00001", "09876500002", "  Kukatpally   " + tag + " ", "lakshmisarees.in/shop", null)));
        assertThat(draft.status()).isEqualTo(SellerStatus.DRAFT);
        assertThat(draft.contactPhone()).isEqualTo("9876500001");
        assertThat(draft.whatsappNumber()).isEqualTo("9876500002");
        assertThat(draft.area()).isEqualTo("Kukatpally " + tag);
        assertThat(draft.websiteUrl()).as("a bare domain is stored as https").isEqualTo("https://lakshmisarees.in/shop");
        for (String bad : List.of("javascript:alert(1)", "mysite", "http://localhost:8080", "ftp://files.example.com",
                "https://user:pass@example.com", "www.exa mple.com")) {
            assertThat(sellers.updateProfile(asha, new SellerDetails("Asha " + tag + " Sarees", SellerCategory.FASHION_SAREE,
                    "9876500001", null, null, bad, null)).error()).as(bad).isEqualTo(MarketplaceError.INVALID_WEBSITE);
        }
        assertThat(draft.listingFee().amount()).isEqualByComparingTo("299");
        assertThat(sellers.applyAsSeller(asha, details("Again", "9876500001", null)).error())
                .isEqualTo(MarketplaceError.ALREADY_A_SELLER);
        assertThat(sellers.submitForReview(asha).error()).isEqualTo(MarketplaceError.NOTHING_TO_REVIEW);

        // ---- products and the photo caps (2 a product, 3 a shop here)
        // A "was" price must be above the price.
        assertThat(products.addProduct(asha, new ProductDetails("x", "y", new BigDecimal("4500"), new BigDecimal("4500"), true)).error())
                .isEqualTo(MarketplaceError.INVALID_ORIGINAL_PRICE);
        SellerView first = ok(products.addProduct(asha, new ProductDetails("Kanjivaram silk " + tag, "Handwoven, pure zari",
                new BigDecimal("4500"), new BigDecimal("6000"), true)));
        UUID silk = first.products().get(0).id();
        String silkCode = first.products().get(0).code();
        assertThat(silkCode).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{7}");
        assertThat(first.products().get(0).originalPrice()).isEqualByComparingTo("6000");
        ok(products.addProductImage(asha, silk, photo()));
        ok(products.addProductImage(asha, silk, photo()));
        assertThat(products.addProductImage(asha, silk, photo()).error()).isEqualTo(MarketplaceError.PRODUCT_IMAGE_LIMIT_REACHED);
        SellerView two = ok(products.addProduct(asha, product("Cotton saree", "Everyday wear", "900")));
        UUID cotton = two.products().get(1).id();
        ok(products.addProductImage(asha, cotton, photo()));
        assertThat(products.addProductImage(asha, cotton, photo()).error()).isEqualTo(MarketplaceError.SELLER_IMAGE_LIMIT_REACHED);

        // ---- someone else cannot touch her shop
        assertThat(products.updateProduct(bina, silk, product("Mine now", "x", "1")).error()).isEqualTo(MarketplaceError.NOT_A_SELLER);
        ok(sellers.applyAsSeller(bina, details("Bina " + tag, "9876500003", null)));
        assertThat(products.updateProduct(bina, silk, product("Mine now", "x", "1")).error()).isEqualTo(MarketplaceError.PRODUCT_NOT_FOUND);
        assertThat(products.deleteProduct(bina, silk).error()).isEqualTo(MarketplaceError.PRODUCT_NOT_FOUND);

        // ---- review: locked while a person looks, rejected with a reason, sent again, approved
        assertThat(ok(sellers.submitForReview(asha)).status()).isEqualTo(SellerStatus.SUBMITTED_FOR_REVIEW);
        assertThat(products.updateProduct(asha, silk, product("Changed", "x", "1")).error()).isEqualTo(MarketplaceError.NOT_EDITABLE);
        UUID sellerId = draft.id();
        assertThat(sellers.reject(sellerId, admin, " ").error()).isEqualTo(MarketplaceError.REASON_REQUIRED);
        assertThat(sellers.suspend(sellerId, admin, "too early").error()).isEqualTo(MarketplaceError.INVALID_TRANSITION);
        assertThat(ok(sellers.reject(sellerId, admin, "Photo 2 is blurred")).rejectionReason()).isEqualTo("Photo 2 is blurred");
        assertThat(sellers.mySeller(asha).orElseThrow().canEdit()).isTrue();
        ok(sellers.submitForReview(asha));
        assertThat(ok(sellers.approve(sellerId, admin)).status()).isEqualTo(SellerStatus.APPROVED_AWAITING_PAYMENT);
        assertThat(directory(null, tag)).isEmpty();

        // ---- the fee, captured by webhook
        ListingFeeCheckout checkout = ok(sellers.startListingFeePayment(asha));
        assertThat(checkout.amountPaise()).isEqualTo(29900);
        // Asking again reuses the same order - one fee, never two.
        assertThat(ok(sellers.startListingFeePayment(asha)).orderId()).isEqualTo(checkout.orderId());
        events.clear();
        assertThat(payments.applyWebhookUpdate(checkout.orderId(), "pay_hook", true, null, PaymentMethod.UPI)).isTrue();

        SellerView live = sellers.mySeller(asha).orElseThrow();
        assertThat(live.status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(live.listingFee().status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(live.listingFee().method()).isEqualTo(PaymentMethod.UPI);
        assertThat(events.stream(ListingFeePaid.class)).hasSize(1);
        assertThat(events.stream(PaymentCaptured.class)).as("a listing fee is not a trip's capture").isEmpty();
        assertThat(events.stream(SellerStatusChanged.class)).anyMatch(e -> e.status() == SellerStatus.ACTIVE);
        assertThat(sellers.startListingFeePayment(asha).error()).isEqualTo(MarketplaceError.ALREADY_PAID);

        // ---- in her payment history, marked as what it is
        PaymentSummary fee = payments.pageForBookings(List.of(), asha, null, null, null, null, null, PageRequest.of(0, 20))
                .getContent().get(0);
        assertThat(fee.purpose()).isEqualTo(PaymentPurpose.SELLER_LISTING_FEE);
        assertThat(fee.bookingId()).isNull();
        assertThat(fee.sellerId()).isEqualTo(sellerId);

        // ---- the directory: category and keyword, shop name included
        assertThat(directory(null, tag)).extracting(ListingCard::title).containsExactlyInAnyOrder("Kanjivaram silk " + tag, "Cotton saree");
        assertThat(directory(SellerCategory.FASHION_SAREE, tag)).hasSize(2);
        assertThat(directory(SellerCategory.MEHANDI, tag)).isEmpty();
        assertThat(directory(null, "kanjivaram SILK " + tag)).singleElement().satisfies(c -> {
            assertThat(c.imageUrl()).isNotNull();
            assertThat(c.businessName()).isEqualTo("Asha " + tag + " Sarees");
        });
        assertThat(directory(null, tag + "-nothing")).isEmpty();

        // ---- a product code finds that one product, typed in any case, with or without #
        assertThat(directory(null, silkCode.toLowerCase())).extracting(ListingCard::productId).containsExactly(silk);
        assertThat(directory(null, " #" + silkCode + " ")).extracting(ListingCard::productId).containsExactly(silk);
        assertThat(two.products().get(1).code()).isNotEqualTo(silkCode);

        // ---- "in your own words" with no model configured: the keyword search answers instead, never an error
        SmartSearchResult asked = products.askListings(bina, new DirectoryFilter(Set.of(), null, null, null, null), "Cotton saree");
        assertThat(asked.mode()).isEqualTo(SmartSearchResult.Mode.EXACT);
        assertThat(asked.reason()).isEqualTo(SmartSearchResult.FallbackReason.UNAVAILABLE);
        assertThat(asked.items()).extracting(ListingCard::title).contains("Cotton saree");
        // A sentence matches nothing as one phrase; the fallback finds it by its words.
        assertThat(products.askListings(bina, new DirectoryFilter(Set.of(), null, null, null, null),
                "looking for something handwoven under 5000").items())
                .extracting(ListingCard::title).contains("Kanjivaram silk " + tag);
        assertThat(ProductService.meaningfulWords("Something for a WEDDING under 2000, please!")).containsExactly("wedding");

        // ---- price range (inclusive), several categories at once, and area
        assertThat(browse(Set.of(), tag, "900", "900", null)).extracting(ListingCard::title).containsExactly("Cotton saree");
        assertThat(browse(Set.of(), tag, "1000", null, null)).extracting(ListingCard::title).containsExactly("Kanjivaram silk " + tag);
        assertThat(browse(Set.of(), tag, null, "899", null)).isEmpty();
        assertThat(browse(Set.of(SellerCategory.MEHANDI, SellerCategory.FASHION_SAREE), tag, null, null, null)).hasSize(2);
        assertThat(browse(Set.of(SellerCategory.MEHANDI, SellerCategory.GIFTS), tag, null, null, null)).isEmpty();
        assertThat(browse(Set.of(), null, null, null, "kukatpally " + tag)).hasSize(2)
                .allSatisfy(c -> assertThat(c.area()).isEqualTo("Kukatpally " + tag));
        assertThat(browse(Set.of(), null, null, null, "Madhapur " + tag)).isEmpty();
        ProductDetail detail = products.getProductDetail(silk).orElseThrow();
        assertThat(detail.whatsappNumber()).isEqualTo("9876500002");
        assertThat(detail.contactPhone()).isEqualTo("9876500001");
        assertThat(detail.imageUrls()).hasSize(2);
        assertThat(detail.moreFromSeller()).extracting(ListingCard::productId).containsExactly(cotton);

        // ---- a live shop can still change - noted for operations - and an inactive product leaves the directory
        ok(products.updateProduct(asha, cotton, new ProductDetails("Cotton saree", "Everyday wear", new BigDecimal("900"), null, false)));
        assertThat(directory(null, tag)).hasSize(1);
        assertThat(products.getProductDetail(cotton)).isEmpty();
        assertThat(sellers.sellerDetail(sellerId).orElseThrow().seller().editedLiveAt()).isNotNull();

        // ---- suspended: gone at once; reinstated: back
        assertThat(ok(sellers.suspend(sellerId, admin, "Complaint: item not as pictured")).status()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(directory(null, tag)).isEmpty();
        assertThat(products.getProductDetail(silk)).isEmpty();
        assertThat(products.addProduct(asha, product("x", "y", "1")).error()).isEqualTo(MarketplaceError.NOT_EDITABLE);
        assertThat(ok(sellers.reinstate(sellerId, admin)).status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(directory(null, tag)).hasSize(1);
    }

    @Test
    void checkoutConfirmationAloneAlsoPutsHerLive() throws IOException {
        SellerView shop = ok(sellers.applyAsSeller(bina, details("Bina " + tag + " Mehandi", "9876500004", null)));
        UUID design = ok(products.addProduct(bina, product("Bridal mehandi", "Full hands", "2500"))).products().get(0).id();
        ok(products.addProductImage(bina, design, photo()));
        assertThat(sellers.startListingFeePayment(bina).error()).isEqualTo(MarketplaceError.NOT_AWAITING_PAYMENT);
        ok(sellers.submitForReview(bina));
        ok(sellers.approve(shop.id(), admin));

        ListingFeeCheckout checkout = ok(sellers.startListingFeePayment(bina));
        SellerView live = ok(sellers.confirmListingFeePayment(bina, checkout.orderId(), "pay_checkout", "sig"));

        assertThat(live.status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(live.listingFee().method()).isEqualTo(PaymentMethod.CARD);
        // The webhook arriving afterwards changes nothing.
        events.clear();
        payments.applyWebhookUpdate(checkout.orderId(), "pay_checkout", true, null, PaymentMethod.UPI);
        assertThat(events.stream(ListingFeePaid.class)).isEmpty();
        assertThat(directory(SellerCategory.MEHANDI, tag)).hasSize(1);
    }

    @Test
    void theFeeCanBePaidFromHerSheOutWallet() throws IOException {
        SellerView shop = ok(sellers.applyAsSeller(bina, details("Bina " + tag + " Gifts", "9876500005", null)));
        UUID p = ok(products.addProduct(bina, product("Hamper", "Festive hamper", "800"))).products().get(0).id();
        ok(products.addProductImage(bina, p, photo()));
        ok(sellers.submitForReview(bina));
        ok(sellers.approve(shop.id(), admin));

        jdbc.update("insert into rider_wallets (id, customer_account_id, balance, version, created_at, updated_at) "
                + "values (gen_random_uuid(), ?, 100, 0, now(), now())", bina);
        assertThat(sellers.payListingFeeFromWallet(bina).error()).isEqualTo(MarketplaceError.INSUFFICIENT_BALANCE);
        assertThat(sellers.mySeller(bina).orElseThrow().status()).isEqualTo(SellerStatus.APPROVED_AWAITING_PAYMENT);

        jdbc.update("update rider_wallets set balance = 500 where customer_account_id = ?", bina);
        SellerView live = ok(sellers.payListingFeeFromWallet(bina));

        assertThat(live.status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(live.listingFee().method()).isEqualTo(PaymentMethod.SHEOUT_WALLET);
        assertThat(jdbc.queryForObject("select balance from rider_wallets where customer_account_id = ?", BigDecimal.class, bina))
                .isEqualByComparingTo("201");
        assertThat(jdbc.queryForObject("select count(*) from rider_wallet_entries where customer_account_id = ? and entry_type = 'LISTING_FEE' and amount = -299",
                Integer.class, bina)).isEqualTo(1);
        assertThat(sellers.payListingFeeFromWallet(bina).error()).isEqualTo(MarketplaceError.ALREADY_PAID);
        assertThat(directory(SellerCategory.FASHION_SAREE, tag)).extracting(ListingCard::title).contains("Hamper");
    }

    @Test
    void otherIsWhatSheSaysSheSellsAndBuyersFindItByThoseWords() throws IOException {
        // Other without saying what, or saying far too much: refused.
        assertThat(sellers.applyAsSeller(bina, other("Bina " + tag, null)).error()).isEqualTo(MarketplaceError.CUSTOM_CATEGORY_REQUIRED);
        assertThat(sellers.applyAsSeller(bina, other("Bina " + tag, " x ")).error()).isEqualTo(MarketplaceError.CUSTOM_CATEGORY_REQUIRED);
        assertThat(sellers.applyAsSeller(bina, other("Bina " + tag, "x".repeat(41))).error()).isEqualTo(MarketplaceError.CUSTOM_CATEGORY_REQUIRED);

        SellerView shop = ok(sellers.applyAsSeller(bina, other("Bina " + tag + " Kitchen", "  Homemade   pickles " + tag + " ")));
        assertThat(shop.category()).isEqualTo(SellerCategory.OTHER);
        assertThat(shop.customCategory()).as("trimmed, spaces folded").isEqualTo("Homemade pickles " + tag);

        UUID p = ok(products.addProduct(bina, product("Mango jar", "500 g, sun-dried", "250"))).products().get(0).id();
        ok(products.addProductImage(bina, p, photo()));
        ok(sellers.submitForReview(bina));
        ok(sellers.approve(shop.id(), admin));
        jdbc.update("insert into rider_wallets (id, customer_account_id, balance, version, created_at, updated_at) "
                + "values (gen_random_uuid(), ?, 500, 0, now(), now())", bina);
        ok(sellers.payListingFeeFromWallet(bina));

        // Under Other, with her words on the card; and found by those words.
        assertThat(directory(SellerCategory.OTHER, tag)).singleElement().satisfies(c -> {
            assertThat(c.category()).isEqualTo(SellerCategory.OTHER);
            assertThat(c.customCategory()).isEqualTo("Homemade pickles " + tag);
        });
        assertThat(directory(null, "pickles " + tag)).extracting(ListingCard::title).containsExactly("Mango jar");
        assertThat(directory(SellerCategory.GIFTS, tag)).isEmpty();

        // A named category has no words of her own: they are cleared.
        SellerView renamed = ok(sellers.updateProfile(bina, new SellerDetails("Bina " + tag + " Kitchen", SellerCategory.GIFTS,
                "9876500005", null, null, null, "leftover words")));
        assertThat(renamed.customCategory()).isNull();
    }

    @Test
    void anUnverifiedAccountCannotSendHerShopForReview() throws IOException {
        doReturn(Optional.empty()).when(customerProfiles).findByAccountId(any());
        ok(sellers.applyAsSeller(asha, details("Asha " + tag, "9876500001", null)));
        UUID p = ok(products.addProduct(asha, product("Blouse stitching", "Custom fit", "600"))).products().get(0).id();
        ok(products.addProductImage(asha, p, photo()));

        assertThat(sellers.mySeller(asha).orElseThrow().canSubmit()).isFalse();
        assertThat(sellers.submitForReview(asha).error()).isEqualTo(MarketplaceError.NOT_VERIFIED);
    }

    // ------------------------------------------------------------ helpers

    private List<ListingCard> directory(SellerCategory category, String keyword) {
        return browse(category == null ? Set.of() : Set.of(category), keyword, null, null, null);
    }

    private List<ListingCard> browse(Set<SellerCategory> categories, String keyword, String minPrice, String maxPrice, String area) {
        DirectoryFilter filter = new DirectoryFilter(categories, keyword,
                minPrice == null ? null : new BigDecimal(minPrice), maxPrice == null ? null : new BigDecimal(maxPrice), area);
        return products.browseListings(filter, PageRequest.of(0, 50)).getContent();
    }

    private static SellerDetails details(String name, String phone, String whatsapp) {
        return new SellerDetails(name, name.contains("Mehandi") ? SellerCategory.MEHANDI : SellerCategory.FASHION_SAREE, phone, whatsapp,
                null, null, null);
    }

    private static SellerDetails other(String name, String whatSheSells) {
        return new SellerDetails(name, SellerCategory.OTHER, "9876500005", null, null, null, whatSheSells);
    }

    private static ProductDetails product(String title, String description, String price) {
        return new ProductDetails(title, description, new BigDecimal(price), null, true);
    }

    private static DocumentUpload photo() throws IOException {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new DocumentUpload("photo.png", "image/png", out.toByteArray());
    }

    private static <T> T ok(Result<T, MarketplaceError> result) {
        assertThat(result.isSuccess()).as(() -> "expected success, got " + (result.isFailure() ? result.error() : "")).isTrue();
        return result.value();
    }
}
