// src/contexts/ThemeContext.tsx
import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { Theme } from '@/types/theme';
import { useQuizSocket } from '@/hooks/useQuizSocket';
import { apiConfig } from '@/config/api';

const SITE_LOCALE_EVENT = 'site:locale-changed';
const SITE_LANG_KEY = 'site:lang';
const DEFAULT_TENANT_ID = 'baronesa';

const normalizeLocale = (value?: string | null): string => {
  if (!value) return 'pt-BR';
  const cleaned = value.replace('_', '-').trim();
  const lower = cleaned.toLowerCase();
  if (lower.startsWith('pt')) return 'pt-BR';
  if (lower.startsWith('en')) return 'en-US';
  if (lower.startsWith('es')) return 'es-ES';
  return cleaned || 'pt-BR';
};

const resolvePreferredLocale = () => {
  try {
    const stored = localStorage.getItem(SITE_LANG_KEY);
    if (stored) return normalizeLocale(stored);
  } catch {
    // ignore
  }
  return normalizeLocale(navigator?.language || navigator?.languages?.[0] || 'pt-BR');
};

interface ThemeContextType {
  theme: Theme | null;
  setTheme: (theme: Theme) => void;
  isLoading: boolean;
  error: string | null;
  refreshTheme: (tenantId?: string) => Promise<void>;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [theme, setTheme] = useState<Theme | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [preferredLocale, setPreferredLocale] = useState<string>(() => resolvePreferredLocale());
  const { subscribe } = useQuizSocket();

  const fetchTheme = useCallback(async (tenantId?: string, localeOverride?: string) => {
    try {
      setIsLoading(true);
      setError(null);

      const tenantFromStorage = localStorage.getItem('currentTenantId');
      const effectiveTenantId = tenantId ?? tenantFromStorage ?? DEFAULT_TENANT_ID;
      const localeHeader = localeOverride || resolvePreferredLocale();
      setPreferredLocale(localeHeader);

      // Adiciona um cachebuster para garantir que o tema mais recente seja carregado
      const cacheBuster = new Date().getTime();
      const params = new URLSearchParams();
      params.set('_cb', cacheBuster.toString());
      if (effectiveTenantId) {
        params.set('tenantId', effectiveTenantId);
      }

      const response = await fetch(`${apiConfig.erpBaseUrl}/api/website/themes/public/theme/active?${params.toString()}`, {
        headers: {
          'Accept-Language': localeHeader,
        },
      });
      if (!response.ok) {
        throw new Error(`Erro ao carregar tema: ${response.status}`);
      }
      const themeData = await response.json();
      if (themeData?.tenantId) {
        localStorage.setItem('currentTenantId', themeData.tenantId);
      }
      setTheme(themeData);
    } catch (err) {
      console.error('Erro ao carregar tema:', err);
      setError(err instanceof Error ? err.message : 'Erro desconhecido ao carregar tema');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const refreshTheme = useCallback(async (tenantId?: string, localeOverride?: string) => {
    await fetchTheme(tenantId, localeOverride);
  }, [fetchTheme]);

  useEffect(() => {
    fetchTheme(undefined, preferredLocale);
  }, [fetchTheme, preferredLocale]);

  useEffect(() => {
    const handleLocaleChange = (event: Event) => {
      const next = (event as CustomEvent).detail as string | undefined;
      const normalized = normalizeLocale(next);
      if (normalized) {
        setPreferredLocale(normalized);
        void refreshTheme(undefined, normalized);
      }
    };
    const handleStorage = (event: StorageEvent) => {
      if (event.key === SITE_LANG_KEY && event.newValue) {
        const normalized = normalizeLocale(event.newValue);
        if (normalized) {
          setPreferredLocale(normalized);
          void refreshTheme(undefined, normalized);
        }
      }
    };
    window.addEventListener(SITE_LOCALE_EVENT, handleLocaleChange);
    window.addEventListener('storage', handleStorage);
    return () => {
      window.removeEventListener(SITE_LOCALE_EVENT, handleLocaleChange);
      window.removeEventListener('storage', handleStorage);
    };
  }, [refreshTheme]);

  useEffect(() => {
    if (!theme?.tokens) {
      return;
    }

    Object.entries(theme.tokens).forEach(([key, value]) => {
      document.documentElement.style.setProperty(`--${key}`, value as string);
    });
  }, [theme?.tokens]);

  const subscriptionTenantId = theme?.tenantId || localStorage.getItem('currentTenantId') || DEFAULT_TENANT_ID;

  useEffect(() => {
    if (!subscribe) {
      return;
    }

    const destination = `/topic/theme/${subscriptionTenantId}/refresh`;
    const unsubscribe = subscribe(destination, (message) => {
      const tenantToRefresh = message?.tenantId || subscriptionTenantId;
      refreshTheme(tenantToRefresh);
    });

    return () => {
      unsubscribe?.();
    };
  }, [subscribe, refreshTheme, subscriptionTenantId]);

  const value = {
    theme,
    setTheme,
    isLoading,
    error,
    refreshTheme
  };

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};
