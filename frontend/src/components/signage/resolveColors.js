// Resolve "elementMappings" (from SignageColorMapper) into "resolvedColors" (template contract).
//
// Mapping rules:
// - "auto": omit key (template will use its own defaults; also keeps resolvedColors empty when nothing is set)
// - "palette:<key>": use palette[key]
// - "custom:<hex>": use provided value
//
// IMPORTANT: Only write keys when a non-empty value is resolved; otherwise templates may think
// resolvedColors is "present" (Object.keys > 0) and skip their theme/default fallback.

const paletteKeyMap = {
  vibrant: "vibrant",
  muted: "muted",
  lightvibrant: "lightVibrant",
  darkvibrant: "darkVibrant",
  lightmuted: "lightMuted",
  darkmuted: "darkMuted",
  background: "background",
  text: "text",
  accent: "accent",
  accent2: "accent2",
};

const normalizePaletteKey = (key) => {
  if (!key) return "";
  const normalized = String(key).trim();
  if (!normalized) return "";
  const k = normalized.toLowerCase();
  return paletteKeyMap[k] || normalized;
};

export function resolveColors(palette = {}, elementMappings = {}) {
  const resolved = {};
  if (!elementMappings || typeof elementMappings !== "object") return resolved;

  for (const [elementKey, rawMapping] of Object.entries(elementMappings)) {
    const mapping = typeof rawMapping === "string" ? rawMapping.trim() : "";
    if (!mapping || mapping === "auto") continue;

    if (mapping.startsWith("palette:")) {
      const paletteKey = normalizePaletteKey(mapping.slice("palette:".length));
      const value = palette?.[paletteKey];
      if (typeof value === "string" && value.trim() !== "") {
        resolved[elementKey] = value;
      }
      continue;
    }

    if (mapping.startsWith("custom:")) {
      const value = mapping.slice("custom:".length).trim();
      if (value) {
        resolved[elementKey] = value;
      }
      continue;
    }
  }

  return resolved;
}

