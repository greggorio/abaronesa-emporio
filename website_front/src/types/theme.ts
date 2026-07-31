// src/types/theme.ts
export interface Theme {
  id: number;
  name: string;
  baseThemeId?: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  tokens: Record<string, string>;
  assets: Record<string, any>;
  content: Record<string, any>;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface ThemeDTO {
  name: string;
  baseThemeId?: number;
  status?: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  tokens?: Record<string, string>;
  assets?: Record<string, any>;
  content?: Record<string, any>;
  tenantId: string;
}

export interface ThemeScheduleDTO {
  themeId: number;
  validFrom?: string;
  validTo?: string;
  priority?: number;
}
