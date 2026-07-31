import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import QuizImportExample from './QuizImportExample';
import apiClient from '@/lib/api-client';

vi.mock('@/lib/api-client', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

describe('QuizImportExample', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    global.URL.createObjectURL = vi.fn(() => 'blob:test');
    global.URL.revokeObjectURL = vi.fn();
  });

  it('should render the component title', () => {
    renderWithProviders(<QuizImportExample />);
    expect(screen.getByText('Importação de Perguntas')).toBeInTheDocument();
  });

  it('should render template download buttons', () => {
    renderWithProviders(<QuizImportExample />);
    expect(screen.getByText('Baixar Template JSON')).toBeInTheDocument();
    expect(screen.getByText('Baixar Template CSV')).toBeInTheDocument();
  });

  it('should render file input with accept attribute', () => {
    renderWithProviders(<QuizImportExample />);
    const fileInput = document.querySelector('input[type="file"]');
    expect(fileInput).toBeInTheDocument();
    expect(fileInput).toHaveAttribute('accept', '.json,.csv');
  });

  it('should render confirm import button', () => {
    renderWithProviders(<QuizImportExample />);
    expect(screen.getByText('Confirmar Importação')).toBeInTheDocument();
  });

  it('should call downloadTemplateJson when JSON button is clicked', async () => {
    (apiClient.get as any).mockResolvedValue({
      data: new Blob(['{}'], { type: 'application/json' }),
    });

    const user = userEvent.setup();
    renderWithProviders(<QuizImportExample />);

    await user.click(screen.getByText('Baixar Template JSON'));

    expect(apiClient.get).toHaveBeenCalledWith('/api/questions/import/template.json', {
      responseType: 'blob',
    });
  });

  it('should call downloadTemplateCsv when CSV button is clicked', async () => {
    (apiClient.get as any).mockResolvedValue({
      data: new Blob(['question,options'], { type: 'text/csv' }),
    });

    const user = userEvent.setup();
    renderWithProviders(<QuizImportExample />);

    await user.click(screen.getByText('Baixar Template CSV'));

    expect(apiClient.get).toHaveBeenCalledWith('/api/questions/import/template.csv', {
      responseType: 'blob',
    });
  });
});
