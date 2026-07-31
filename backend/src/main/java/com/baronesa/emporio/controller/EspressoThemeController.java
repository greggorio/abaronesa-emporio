package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.EspressoThemeClient;
import com.baronesa.emporio.service.ThemeTranslationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/website/themes/public/theme")
@RequiredArgsConstructor
public class EspressoThemeController {

    private final EspressoThemeClient espressoThemeClient;
    private final ThemeTranslationMapper themeTranslationMapper;

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveTheme(@RequestParam String tenantId,
                                                              @RequestParam(name = "_cb", required = false) String cb,
                                                              @RequestHeader(name = "X-Mark-Translations", required = false, defaultValue = "false") boolean markTranslations,
                                                              Locale locale) {
        if (!StringUtils.hasText(tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId é obrigatório");
        }

        Map<String, Object> theme = espressoThemeClient.getActiveTheme(tenantId, cb);
        Map<String, Object> translated = themeTranslationMapper.translateTheme(theme, locale, markTranslations);
        return ResponseEntity.ok(translated);
    }
}
