package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.TranslationResponse;
import com.baronesa.emporio.dto.TranslationUpdateRequest;
import com.baronesa.emporio.entity.EntityTranslation;
import com.baronesa.emporio.entity.TranslationStatus;
import com.baronesa.emporio.repository.EntityTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/admin/translations")
@RequiredArgsConstructor
public class TranslationAdminController {

    private final EntityTranslationRepository repository;

    @GetMapping
    public Page<TranslationResponse> list(@RequestParam(value = "locale", required = false) String locale,
                                          @RequestParam(value = "status", required = false) TranslationStatus status,
                                          @RequestParam(value = "entityType", required = false) String entityType,
                                          @RequestParam(value = "entityId", required = false) Long entityId,
                                          @RequestParam(value = "field", required = false) String field,
                                          @RequestParam(value = "search", required = false) String search,
                                          @RequestParam(value = "page", defaultValue = "0") int page,
                                          @RequestParam(value = "size", defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return repository.search(locale, status, entityType, entityId, field, search, pageable)
                .map(TranslationResponse::fromEntity);
    }

    @PutMapping("/{id}")
    public TranslationResponse update(@PathVariable Long id, @RequestBody TranslationUpdateRequest request) {
        EntityTranslation t = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Translation not found: " + id));
        if (request.getTranslatedText() != null) {
            t.setTranslatedText(request.getTranslatedText());
        }
        if (request.getStatus() != null) {
            t.setStatus(request.getStatus());
        }
        t.setProvider("MANUAL");
        return TranslationResponse.fromEntity(repository.save(t));
    }

    @PostMapping("/{id}/regenerate")
    public TranslationResponse regenerate(@PathVariable Long id) {
        EntityTranslation t = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Translation not found: " + id));
        t.setStatus(TranslationStatus.PENDING);
        t.setProvider(null);
        t.setTranslatedText(null);
        return TranslationResponse.fromEntity(repository.save(t));
    }
}
