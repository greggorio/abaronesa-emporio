import { useCallback, useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import ptBR from "./site.pt-BR.json";
import enUS from "./site.en-US.json";
import esES from "./site.es-ES.json";

type LocaleTag = "pt-BR" | "en-US" | "es-ES";
type TranslationBundle = Record<string, string>;

const FALLBACK_LOCALE: LocaleTag = "pt-BR";
const LOCALE_BUNDLES: Record<LocaleTag, TranslationBundle> = {
  "pt-BR": ptBR,
  "en-US": enUS,
  "es-ES": esES,
};

const availableLocales = Object.keys(LOCALE_BUNDLES);
const localStorageKey = "site:lang";
const canUseWindow = typeof window !== "undefined";
const localeChangeEvent = "site:locale-changed";

const normalizeLocaleTag = (value?: string | null): LocaleTag | null => {
  if (!value) return null;
  const cleaned = value.replace("_", "-").trim();
  const normalized = availableLocales.find((locale) => locale.toLowerCase() === cleaned.toLowerCase());
  if (normalized) return normalized as LocaleTag;
  const prefix = cleaned.split("-")[0].toLowerCase();
  if (prefix === "pt") return "pt-BR";
  if (prefix === "en") return "en-US";
  if (prefix === "es") return "es-ES";
  return null;
};

const resolveLocale = (search: string): LocaleTag => {
  const queryParam = new URLSearchParams(search).get("lang");
  const fromQuery = normalizeLocaleTag(queryParam);
  if (fromQuery) return fromQuery;

  if (canUseWindow) {
    try {
      const saved = window.localStorage.getItem(localStorageKey);
      const storedLocale = normalizeLocaleTag(saved);
      if (storedLocale) return storedLocale;
    } catch {
      // ignore storage errors
    }
  }

  if (canUseWindow) {
    const navLanguage = navigator.language || navigator.languages?.[0];
    const navLocale = normalizeLocaleTag(navLanguage);
    if (navLocale) return navLocale;
  }

  return FALLBACK_LOCALE;
};

const replacePlaceholders = (text: string, values?: Record<string, string | number>): string =>
  text.replace(/\{\{(\w+)\}\}/g, (_, key) => {
    if (!values) return "";
    const val = values[key];
    return val !== undefined && val !== null ? String(val) : "";
  });

export function useSiteI18n() {
  const location = useLocation();
  const [locale, setLocaleState] = useState<LocaleTag>(() => resolveLocale(location.search));

  useEffect(() => {
    setLocaleState(resolveLocale(location.search));
  }, [location.search]);

  const setLocale = useCallback((nextLocale: LocaleTag) => {
    setLocaleState(nextLocale);
    if (canUseWindow) {
      try {
        window.localStorage.setItem(localStorageKey, nextLocale);
        window.dispatchEvent(new CustomEvent(localeChangeEvent, { detail: nextLocale }));
      } catch {
        // ignore write errors
      }
    }
  }, []);

  const bundle = LOCALE_BUNDLES[locale] ?? LOCALE_BUNDLES[FALLBACK_LOCALE];
  const fallbackBundle = LOCALE_BUNDLES[FALLBACK_LOCALE];

  const t = useCallback(
    (key: string, variables?: Record<string, string | number>) => {
      const template = bundle[key] ?? fallbackBundle[key] ?? key;
      return replacePlaceholders(template, variables);
    },
    [bundle, fallbackBundle]
  );

  useEffect(() => {
    if (!canUseWindow) return;
    const handleStorage = (event: StorageEvent) => {
      if (event.key === localStorageKey && event.newValue) {
        const next = normalizeLocaleTag(event.newValue);
        if (next) setLocaleState(next);
      }
    };
    const handleLocaleEvent = (event: Event) => {
      const detail = (event as CustomEvent).detail;
      const next = normalizeLocaleTag(detail);
      if (next) setLocaleState(next);
    };
    window.addEventListener("storage", handleStorage);
    window.addEventListener(localeChangeEvent, handleLocaleEvent);
    return () => {
      window.removeEventListener("storage", handleStorage);
      window.removeEventListener(localeChangeEvent, handleLocaleEvent);
    };
  }, []);

  const formatCurrency = useCallback(
    (value: number, options: { currency?: string; fromCents?: boolean } = { currency: "BRL", fromCents: true }) => {
      const amount = options.fromCents ? value / 100 : value;
      const formatter = new Intl.NumberFormat(locale, {
        style: "currency",
        currency: options.currency ?? "BRL",
      });
      return formatter.format(amount);
    },
    [locale]
  );

  const formatDate = useCallback(
    (value: Date | string | number, options?: Intl.DateTimeFormatOptions) => {
      const date = value instanceof Date ? value : new Date(value);
      if (Number.isNaN(date.getTime())) return "";
      return new Intl.DateTimeFormat(locale, options ?? { dateStyle: "medium" }).format(date);
    },
    [locale]
  );

  return { locale, setLocale, t, formatCurrency, formatDate };
}
