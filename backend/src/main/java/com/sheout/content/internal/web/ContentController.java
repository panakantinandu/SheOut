package com.sheout.content.internal.web;

import com.sheout.content.ContentApi;
import com.sheout.content.ContentBlock;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The apps' read of editable copy. Unauthenticated: it is the text on public
 * screens, and Help & Support must render for someone whose session has
 * expired. No write here - edits are operator-only, through the admin module.
 * <p>
 * Returns key-to-value only. Version, description and who edited it are for
 * the console, not for the apps.
 */
@RestController
@RequestMapping("/api/v1/content")
public class ContentController {

    private final ContentApi contentApi;

    ContentController(ContentApi contentApi) {
        this.contentApi = contentApi;
    }

    /** A whole section, e.g. prefix=faq.customer. - the apps' main read. */
    @GetMapping
    public ResponseEntity<Map<String, String>> section(
            @RequestParam @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9.]+") String prefix) {
        List<ContentBlock> blocks = contentApi.getContentByPrefix(prefix);
        return ResponseEntity.ok(blocks.stream().collect(Collectors.toMap(
                ContentBlock::key, ContentBlock::value, (a, b) -> a, java.util.TreeMap::new)));
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, String>> one(@PathVariable @Size(max = 100) String key) {
        return contentApi.getContent(key)
                .map(block -> ResponseEntity.ok(Map.of(block.key(), block.value())))
                .orElseThrow(() -> ApiException.notFound("No such content"));
    }
}
