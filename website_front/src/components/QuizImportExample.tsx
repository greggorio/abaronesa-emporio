// Exemplo de uso do hook useQuestionImport

import { useQuestionImport } from '../hooks/useQuestionImport';

const QuizImportExample = () => {
  const { previewImport, commitImport, downloadTemplateJson, downloadTemplateCsv } = useQuestionImport();

  // Exemplo de uso do preview
  const handlePreview = async (file: File) => {
    const request = {
      dedupeMode: 'SKIP_DUPLICATES' as const,
      activeMode: 'INHERIT' as const,
      transactionMode: 'PARTIAL_OK' as const,
      dryRun: true,
      previewLimit: 20
    };

    try {
      const result = await previewImport.mutateAsync({ file, request });
      console.log('Preview result:', result);
      // Processar o resultado do preview
    } catch (error) {
      console.error('Erro no preview:', error);
    }
  };

  // Exemplo de uso do commit
  const handleCommit = async (importId: string) => {
    try {
      const result = await commitImport.mutateAsync({ importId });
      console.log('Commit result:', result);
      
      // Verificar se houve erro no commit (caso de 422)
      if (result.summary?.errorCount && result.summary?.errorCount > 0) {
        console.log('Erros encontrados no commit:', result.items);
      }
    } catch (error) {
      console.error('Erro no commit:', error);
    }
  };

  // Exemplo de uso dos templates
  const handleDownloadJsonTemplate = () => {
    downloadTemplateJson();
  };

  const handleDownloadCsvTemplate = () => {
    downloadTemplateCsv();
  };

  return (
    <div>
      <h2>Importação de Perguntas</h2>
      <button onClick={handleDownloadJsonTemplate}>Baixar Template JSON</button>
      <button onClick={handleDownloadCsvTemplate}>Baixar Template CSV</button>
      
      {/* Componente de upload de arquivo para preview */}
      <input 
        type="file" 
        accept=".json,.csv" 
        onChange={(e) => {
          if (e.target.files && e.target.files[0]) {
            handlePreview(e.target.files[0]);
          }
        }} 
      />
      
      {/* Botão para commit (exemplo com importId fictício) */}
      <button onClick={() => handleCommit('some-import-id')}>
        Confirmar Importação
      </button>
    </div>
  );
};

export default QuizImportExample;