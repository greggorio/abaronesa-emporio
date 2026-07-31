package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.regex.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSignagePalette {

    private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private String vibrant;
    private String muted;
    private String lightVibrant;
    private String darkVibrant;
    private String lightMuted;
    private String darkMuted;
    private String background;
    private String text;
    private String accent;
    private String accent2;
    private Boolean isDark;

    public static boolean isValidHex(String color) {
        return color != null && HEX_PATTERN.matcher(color).matches();
    }

    public static ProductSignagePalette fromMap(Map<String, ?> raw) {
        if (raw == null) {
            return null;
        }
        var vibrant = normalizedHex(raw.get("vibrant"));
        var muted = normalizedHex(raw.get("muted"));
        var lightVibrant = normalizedHex(raw.get("lightVibrant"));
        var darkVibrant = normalizedHex(raw.get("darkVibrant"));
        var lightMuted = normalizedHex(raw.get("lightMuted"));
        var darkMuted = normalizedHex(raw.get("darkMuted"));

        var background = firstNonNullHex(raw, "background", "bgColor");
        var text = firstNonNullHex(raw, "text", "textColor");
        var accent = firstNonNullHex(raw, "accent", "accentColor", "brandColor");
        var accent2 = firstNonNullHex(raw, "accent2", "brandColor", "accentColor");

        var legacyBrand = normalizedHex(raw.get("brandColor"));
        if (accent == null && legacyBrand != null) {
            accent = legacyBrand;
        }
        if (accent2 == null && legacyBrand != null) {
            accent2 = legacyBrand;
        }

        var isDark = toBoolean(raw.get("isDark"));

        if (background == null && (accent != null || accent2 != null)) {
            background = "#F5F5F5";
        }

        return ProductSignagePalette.builder()
                .vibrant(vibrant)
                .muted(muted)
                .lightVibrant(lightVibrant)
                .darkVibrant(darkVibrant)
                .lightMuted(lightMuted)
                .darkMuted(darkMuted)
                .background(background)
                .text(text)
                .accent(accent)
                .accent2(accent2)
                .isDark(isDark)
                .build();
    }

    public static ProductSignagePalette fromLegacyPalette(String brandColor, String accentColor, String bgColor, String textColor) {
        var background = normalizedHex(bgColor);
        var text = normalizedHex(textColor);
        var accent = normalizedHex(accentColor);
        var accent2 = normalizedHex(brandColor);
        return ProductSignagePalette.builder()
                .background(background)
                .text(text)
                .accent(accent != null ? accent : accent2)
                .accent2(accent2 != null ? accent2 : accent)
                .isDark(isDark(background))
                .build();
    }

    public static boolean isDark(String background) {
        if (background == null) {
            return false;
        }
        var rgb = toRgb(background);
        if (rgb == null) {
            return false;
        }
        var luminance = computeLuminance(rgb);
        return luminance < 0.5;
    }

    public static String normalizedHex(Object value) {
        if (value == null) {
            return null;
        }
        var str = value.toString().trim();
        if (str.isEmpty()) {
            return null;
        }
        if (!str.startsWith("#")) {
            str = "#" + str;
        }
        return isValidHex(str) ? str.toUpperCase() : null;
    }

    private static String firstNonNullHex(Map<String, ?> map, String... keys) {
        for (var key : keys) {
            var candidate = normalizedHex(map.get(key));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if (s.equalsIgnoreCase("true")) {
                return true;
            }
            if (s.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return null;
    }

    private static Integer[] toRgb(String hex) {
        if (!isValidHex(hex)) {
            return null;
        }
        var clean = hex.replace("#", "");
        return new Integer[]{
                Integer.parseInt(clean.substring(0, 2), 16),
                Integer.parseInt(clean.substring(2, 4), 16),
                Integer.parseInt(clean.substring(4, 6), 16)
        };
    }

    private static double computeLuminance(Integer[] rgb) {
        var normalized = new double[3];
        for (var i = 0; i < 3; i++) {
            var channel = rgb[i] / 255d;
            normalized[i] = channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * normalized[0] + 0.7152 * normalized[1] + 0.0722 * normalized[2];
    }

}
