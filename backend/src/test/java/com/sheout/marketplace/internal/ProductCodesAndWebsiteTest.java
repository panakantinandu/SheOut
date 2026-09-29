package com.sheout.marketplace.internal;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCodesAndWebsiteTest {

    @Test
    void codesAreSevenCharactersWithNoLookAlikes() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String code = ProductCodes.next();
            assertThat(code).hasSize(7).doesNotContainPattern("[0O1IL]").matches("[A-Z2-9]{7}");
            seen.add(code);
        }
        assertThat(seen).hasSizeGreaterThan(4990);
    }

    @Test
    void onlyCodeShapedSearchesAreTreatedAsCodes() {
        assertThat(ProductCodes.normalize(" #7k9m2xq ")).isEqualTo("7K9M2XQ");
        assertThat(ProductCodes.normalize("saree")).isNull();
        assertThat(ProductCodes.normalize("7K9M2X0")).as("0 is never in a code").isNull();
        assertThat(ProductCodes.normalize("7K9M2XQA")).isNull();
    }

    @Test
    void websitesAreHttpAddressesOnRealDomains() {
        assertThat(WebsiteAddress.normalize("www.lakshmisarees.in")).isEqualTo("https://www.lakshmisarees.in");
        assertThat(WebsiteAddress.normalize(" http://shop.co.in/sarees?x=1 ")).isEqualTo("http://shop.co.in/sarees?x=1");
        assertThat(WebsiteAddress.normalize("https://instagram.com/henna.by.farah")).isEqualTo("https://instagram.com/henna.by.farah");
        for (String bad : new String[] {"javascript:alert(1)", "data:text/html,hi", "mysite", "https://192.168.1.1",
                "https://user:pw@shop.com", "shop .com", "ftp://shop.com", "https://" + "a".repeat(200) + ".com"}) {
            assertThat(WebsiteAddress.normalize(bad)).as(bad).isNull();
        }
    }
}
