import React, { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useQuestionImport } from '@/hooks/useQuestionImport';
import { useCategories } from '@/hooks/useCategories';
import { generateQuizQuestions, QuizAiQuestion } from '@/services/quizAiService';
import { Textarea } from '@/components/ui/textarea';

interface QuestionImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImported?: () => void | Promise<void>;  // Callback opcional para quando a importação for concluída
}

type Step = 'upload' | 'config' | 'preview' | 'commit';
type Tab = 'file' | 'ai';

export const QuestionImportDialog: React.FC<QuestionImportDialogProps> = ({ 
  open, 
  onOpenChange, 
  onImported 
}) => {
  const [step, setStep] = useState<Step>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('file');
  const [aiPrompt, setAiPrompt] = useState('Tema: ');
  const [aiQuantity, setAiQuantity] = useState<number>(5);
  const [aiDifficulty, setAiDifficulty] = useState('facil');
  const [aiCategoryId, setAiCategoryId] = useState<number | undefined>(undefined);
  const [aiPoints, setAiPoints] = useState<number | undefined>(10);
  const [aiPreview, setAiPreview] = useState<QuizAiQuestion[] | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [config, setConfig] = useState({
    dedupeMode: 'SKIP_DUPLICATES' as const,
    activeMode: 'INHERIT' as const,
    transactionMode: 'PARTIAL_OK' as const,
    previewLimit: 20,
    dryRun: true
  });
  
  const [previewResult, setPreviewResult] = useState<any>(null);
  const [commitResult, setCommitResult] = useState<any>(null);
  const [isCommitting, setIsCommitting] = useState(false);

  const { previewImport, commitImport, downloadTemplateJson, downloadTemplateCsv } = useQuestionImport();
  const { categories, isLoading: isLoadingCategories, isError: isErrorCategories, refetch: refetchCategories } = useCategories();

  const resetState = () => {
    setStep('upload');
    setFile(null);
    setPreviewResult(null);
    setCommitResult(null);
    setIsCommitting(false);
    setActiveTab('file');
    setAiPreview(null);
    setAiPrompt('Tema: ');
    setAiQuantity(5);
    setAiDifficulty('facil');
    setAiCategoryId(undefined);
    setAiPoints(10);
    setIsGenerating(false);
  };

  useEffect(() => {
    if (!open) {
      resetState();
    } else {
      refetchCategories();
    }
  }, [open]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
    }
  };

  const handlePreview = async () => {
    if (activeTab === 'file') {
      if (!file) return;
      const request = { ...config, dryRun: true };
      try {
        const result = await previewImport.mutateAsync({ file, request });
        setPreviewResult(result);
        setStep('preview');
      } catch (error) {
        console.error('Erro no preview:', error);
      }
    } else {
      try {
        setIsGenerating(true);
        const tema = aiPrompt.trim().length > 0 ? aiPrompt.trim() : 'Tema geral';
        const payload = {
          tema,
          quantidade: aiQuantity,
          dificuldade: aiDifficulty,
          categoryId: aiCategoryId,
          points: aiPoints,
          idioma: 'pt-BR',
        };
        const generated = await generateQuizQuestions(payload);
        setAiPreview(generated);
        // Converter para arquivo JSON em memória para reaproveitar fluxo de import
        const blob = new Blob([JSON.stringify(generated, null, 2)], { type: 'application/json' });
        const aiFile = new File([blob], 'quiz-ai.json', { type: 'application/json' });
        setFile(aiFile);
        // Agora roda preview usando o arquivo gerado
        const request = { ...config, dryRun: true };
        const result = await previewImport.mutateAsync({ file: aiFile, request });
        setPreviewResult(result);
        setStep('preview');
      } catch (error) {
        console.error('Erro ao gerar via IA:', error);
      } finally {
        setIsGenerating(false);
      }
    }
  };

  const handleCommit = async () => {
    if (!file) return;

    setIsCommitting(true);
    try {
      const requestPayload = {
        ...config,
        dryRun: false,
      };
      const result = await commitImport.mutateAsync({ file, request: requestPayload });
      setCommitResult(result);

      const isAllOrNothingWithError = result.summary?.transactionModeUsed === 'ALL_OR_NOTHING' &&
                                     result.summary?.errorCount > 0;

      if (!isAllOrNothingWithError) {
        await onImported?.();
      }

      setStep('commit');
    } catch (error) {
      console.error('Erro no commit:', error);
      setCommitResult({
        summary: { errorCount: 1 },
        items: [],
        error: String(error),
      });
      setStep('commit');
    } finally {
      setIsCommitting(false);
    }
  };

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case 'IMPORTED':
      case 'WILL_IMPORT':
        return 'bg-green-500';
      case 'UPDATED':
      case 'WILL_UPDATE':
        return 'bg-blue-500';
      case 'SKIPPED':
        return 'bg-yellow-500';
      case 'ERROR':
        return 'bg-red-500';
      case 'WARN':
        return 'bg-orange-500';
      default:
        return 'bg-gray-500';
    }
  };

  const renderUploadStep = () => (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
        <div className="text-sm text-[#2A1F1B] font-semibold">Como você quer importar?</div>
        <div className="flex gap-2">
          <Button
            variant={activeTab === 'file' ? 'default' : 'outline'}
            className={`${activeTab === 'file' ? 'bg-[#D7B899] text-[#2A1F1B]' : 'border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]'} flex-1`}
            onClick={() => setActiveTab('file')}
          >
            Upload de arquivo
          </Button>
          <Button
            variant={activeTab === 'ai' ? 'default' : 'outline'}
            className={`${activeTab === 'ai' ? 'bg-[#D7B899] text-[#2A1F1B]' : 'border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]'} flex-1`}
            onClick={() => setActiveTab('ai')}
          >
            Gerar com IA
          </Button>
        </div>
        <div className="text-xs text-gray-600">
          Escolha entre enviar um JSON/CSV pronto ou pedir para a IA gerar as questões para você.
        </div>
      </div>

      {activeTab === 'file' ? (
        <div className="space-y-4">
          <div>
            <Label htmlFor="file-upload">Selecione o arquivo de perguntas</Label>
            <Input
              id="file-upload"
              type="file"
              accept=".json,.csv"
              onChange={handleFileChange}
              className="mt-2"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              onClick={downloadTemplateJson}
              className="border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]"
            >
              Baixar template JSON
            </Button>
            <Button
              variant="outline"
              onClick={downloadTemplateCsv}
              className="border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]"
            >
              Baixar template CSV
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4 border border-[#D7B899] rounded-lg p-4 bg-[#FDF9F4]">
          <div className="grid gap-3">
            <div>
              <Label htmlFor="ai-prompt">Tema / Prompt</Label>
              <Textarea
                id="ai-prompt"
                value={aiPrompt}
                onChange={(e) => setAiPrompt(e.target.value)}
                placeholder="Ex: Crie perguntas fáceis sobre cafeteria"
                className="mt-2"
              />
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="ai-qty">Quantidade</Label>
                <Input
                  id="ai-qty"
                  type="number"
                  min={1}
                  max={20}
                  value={aiQuantity}
                  onChange={(e) => setAiQuantity(parseInt(e.target.value) || 1)}
                />
              </div>
              <div>
                <Label htmlFor="ai-diff">Dificuldade</Label>
                <Select value={aiDifficulty} onValueChange={(v) => setAiDifficulty(v)}>
                  <SelectTrigger className="bg-white border-[#D7B899] text-[#2A1F1B]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="facil">Fácil</SelectItem>
                    <SelectItem value="medio">Médio</SelectItem>
                    <SelectItem value="dificil">Difícil</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="ai-category">Categoria</Label>
                <Select
                  value={aiCategoryId !== undefined ? String(aiCategoryId) : 'none'}
                  onValueChange={(value) => setAiCategoryId(value === 'none' ? undefined : parseInt(value))}
                  disabled={isLoadingCategories}
                >
                  <SelectTrigger className="bg-white border-[#D7B899] text-[#2A1F1B]">
                    <SelectValue placeholder={isLoadingCategories ? 'Carregando...' : 'Selecionar'} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">Nenhuma (deixe a IA sugerir)</SelectItem>
                    {!isErrorCategories && categories?.map((cat: any) => (
                      <SelectItem key={cat.id} value={String(cat.id)}>
                        {cat.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label htmlFor="ai-points">Pontos (opcional)</Label>
                <Input
                  id="ai-points"
                  type="number"
                  value={aiPoints ?? ''}
                  onChange={(e) => setAiPoints(e.target.value ? parseInt(e.target.value) : undefined)}
                />
              </div>
            </div>
          </div>

          {aiPreview && (
            <div className="bg-white p-3 rounded border border-[#D7B899]">
              <div className="font-semibold mb-2 text-[#2A1F1B]">Prévia gerada ({aiPreview.length} itens)</div>
              <ul className="space-y-2 text-sm text-[#2A1F1B]">
                {aiPreview.slice(0, 3).map((q) => (
                  <li key={q.index} className="truncate">{q.index}. {q.question}</li>
                ))}
                {aiPreview.length > 3 && <li>... e mais {aiPreview.length - 3}</li>}
              </ul>
            </div>
          )}
        </div>
      )}

      <div className="flex justify-end">
        <Button
          onClick={() => activeTab === 'file' ? (file && setStep('config')) : handlePreview()}
          disabled={activeTab === 'file' ? !file : isGenerating}
          className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B]"
        >
          {activeTab === 'ai' ? (isGenerating ? 'Gerando...' : 'Gerar e Pré-visualizar') : 'Continuar'}
        </Button>
      </div>
    </div>
  );

  const renderConfigStep = () => (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <Label>Dedupe Mode</Label>
          <Select 
            value={config.dedupeMode} 
            onValueChange={(value) => setConfig({...config, dedupeMode: value as any})}
          >
            <SelectTrigger className="bg-white border-[#D7B899] text-[#2A1F1B]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALLOW_DUPLICATES">Permitir Duplicados</SelectItem>
              <SelectItem value="SKIP_DUPLICATES">Pular Duplicados</SelectItem>
              <SelectItem value="UPDATE_EXISTING">Atualizar Existentes</SelectItem>
            </SelectContent>
          </Select>
        </div>
        
        <div>
          <Label>Active Mode</Label>
          <Select 
            value={config.activeMode} 
            onValueChange={(value) => setConfig({...config, activeMode: value as any})}
          >
            <SelectTrigger className="bg-white border-[#D7B899] text-[#2A1F1B]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="INHERIT">Herdar</SelectItem>
              <SelectItem value="FORCE_ACTIVE">Forçar Ativo</SelectItem>
              <SelectItem value="FORCE_INACTIVE">Forçar Inativo</SelectItem>
            </SelectContent>
          </Select>
        </div>
        
        <div>
          <Label>Transaction Mode</Label>
          <Select 
            value={config.transactionMode} 
            onValueChange={(value) => setConfig({...config, transactionMode: value as any})}
          >
            <SelectTrigger className="bg-white border-[#D7B899] text-[#2A1F1B]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PARTIAL_OK">Parcial OK</SelectItem>
              <SelectItem value="ALL_OR_NOTHING">Tudo ou Nada</SelectItem>
            </SelectContent>
          </Select>
        </div>
        
        <div>
          <Label htmlFor="preview-limit">Limite de Pré-visualização</Label>
          <Input 
            id="preview-limit" 
            type="number" 
            value={config.previewLimit} 
            onChange={(e) => setConfig({...config, previewLimit: parseInt(e.target.value) || 20})}
            className="bg-white border-[#D7B899] text-[#2A1F1B]"
          />
        </div>
      </div>
      
      <div className="flex justify-between">
        <Button 
          variant="outline" 
          onClick={() => setStep('upload')}
          className="border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]"
        >
          Voltar
        </Button>
        <Button 
          onClick={handlePreview}
          disabled={previewImport.isPending}
          className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B]"
        >
          {previewImport.isPending ? 'Processando...' : 'Pré-visualizar'}
        </Button>
      </div>
    </div>
  );

  const renderPreviewStep = () => (
    <div className="space-y-6">
      {previewResult && (
        <>
          <div className="bg-[#F5EDE6] p-4 rounded-lg border border-[#D7B899]">
            <h3 className="font-semibold text-[#2A1F1B] mb-2">Resumo da Pré-visualização</h3>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2 text-sm">
              <div>Total Recebido: <span className="font-medium">{previewResult.summary?.totalReceived}</span></div>
              <div>Total Processado: <span className="font-medium">{previewResult.summary?.totalParsed}</span></div>
              <div>Importado: <span className="font-medium">{previewResult.summary?.importedCount}</span></div>
              <div>Atualizado: <span className="font-medium">{previewResult.summary?.updatedCount}</span></div>
              <div>Pulado: <span className="font-medium">{previewResult.summary?.skippedCount}</span></div>
              <div>Erros: <span className="font-medium text-red-600">{previewResult.summary?.errorCount}</span></div>
            </div>
          </div>
          
          <div>
            <h3 className="font-semibold text-[#2A1F1B] mb-2">Itens de Pré-visualização</h3>
            <div className="border rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow className="bg-[#F5EDE6]">
                    <TableHead className="text-[#2A1F1B]">Status</TableHead>
                    <TableHead className="text-[#2A1F1B]">Pergunta</TableHead>
                    <TableHead className="text-[#2A1F1B]">Categoria</TableHead>
                    <TableHead className="text-[#2A1F1B]">Mensagens</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {previewResult.previewItems?.map((item: any, index: number) => (
                    <TableRow key={index}>
                      <TableCell>
                        <Badge className={`${getStatusBadgeVariant(item.status)} text-white`}>
                          {item.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-xs truncate">{item.question}</TableCell>
                      <TableCell>{item.categoryIdResolved}</TableCell>
                      <TableCell>
                        <div className="max-w-xs">
                          {item.messages?.join(', ') || '-'}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </div>
        </>
      )}
      
      <div className="flex justify-between">
        <Button 
          variant="outline" 
          onClick={() => setStep('config')}
          className="border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]"
        >
          Voltar
        </Button>
        <Button
          onClick={handleCommit}
          disabled={isCommitting || !previewResult?.importId}
          className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B]"
        >
          {isCommitting ? 'Confirmando...' : 'Confirmar Importação'}
        </Button>
      </div>
    </div>
  );

  const renderCommitStep = () => (
    <div className="space-y-6">
      {commitResult && (
        <>
          <div className={`p-4 rounded-lg border ${
            (commitResult.summary?.transactionModeUsed === 'ALL_OR_NOTHING' && 
             commitResult.summary?.errorCount > 0) 
              ? 'bg-red-100 border-red-300' 
              : 'bg-[#F5EDE6] border-[#D7B899]'
          }`}>
            <h3 className="font-semibold text-[#2A1F1B] mb-2">Resultado da Importação</h3>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2 text-sm">
              <div>Total Recebido: <span className="font-medium">{commitResult.summary?.totalReceived}</span></div>
              <div>Total Processado: <span className="font-medium">{commitResult.summary?.totalParsed}</span></div>
              <div>Importado: <span className="font-medium">{commitResult.summary?.importedCount}</span></div>
              <div>Atualizado: <span className="font-medium">{commitResult.summary?.updatedCount}</span></div>
              <div>Pulado: <span className="font-medium">{commitResult.summary?.skippedCount}</span></div>
              <div>Erros: <span className="font-medium text-red-600">{commitResult.summary?.errorCount}</span></div>
            </div>
            {commitResult.summary?.transactionModeUsed === 'ALL_OR_NOTHING' && 
             commitResult.summary?.errorCount > 0 && (
              <div className="mt-2 text-red-600 font-medium">
                Importação falhou devido ao modo "Tudo ou Nada" e erros encontrados
              </div>
            )}
          </div>
          
          <div>
            <h3 className="font-semibold text-[#2A1F1B] mb-2">Itens Processados</h3>
            <div className="border rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow className="bg-[#F5EDE6]">
                    <TableHead className="text-[#2A1F1B]">Status</TableHead>
                    <TableHead className="text-[#2A1F1B]">Pergunta</TableHead>
                    <TableHead className="text-[#2A1F1B]">Mensagens</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {commitResult.items?.map((item: any, index: number) => (
                    <TableRow key={index}>
                      <TableCell>
                        <Badge className={`${getStatusBadgeVariant(item.status)} text-white`}>
                          {item.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-xs truncate">{item.question}</TableCell>
                      <TableCell>
                        <div className="max-w-xs">
                          {item.messages?.join(', ') || '-'}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </div>
        </>
      )}
      
      <div className="flex justify-end">
        <Button 
          onClick={() => {
            setStep('upload');
            setFile(null);
            setPreviewResult(null);
            setCommitResult(null);
            onOpenChange(false);
          }}
          className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B]"
        >
          Fechar
        </Button>
      </div>
    </div>
  );

  const renderStepContent = () => {
    switch (step) {
      case 'upload':
        return renderUploadStep();
      case 'config':
        return renderConfigStep();
      case 'preview':
        return renderPreviewStep();
      case 'commit':
        return renderCommitStep();
      default:
        return renderUploadStep();
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-[#2A1F1B]">
            Importar Perguntas - {step === 'upload' ? 'Upload' : step === 'config' ? 'Configuração' : step === 'preview' ? 'Pré-visualização' : 'Confirmação'}
          </DialogTitle>
        </DialogHeader>
        
        <div className="py-4">
          <Tabs value={step} className="w-full">
            <TabsList className="grid w-full grid-cols-4 mb-6">
              <TabsTrigger value="upload" className={step === 'upload' ? 'bg-[#D7B899]' : ''}>Upload</TabsTrigger>
              <TabsTrigger value="config" className={step === 'config' ? 'bg-[#D7B899]' : ''}>Configuração</TabsTrigger>
              <TabsTrigger value="preview" className={step === 'preview' ? 'bg-[#D7B899]' : ''}>Pré-visualização</TabsTrigger>
              <TabsTrigger value="commit" className={step === 'commit' ? 'bg-[#D7B899]' : ''}>Confirmação</TabsTrigger>
            </TabsList>
          </Tabs>
          
          {renderStepContent()}
        </div>
      </DialogContent>
    </Dialog>
  );
};
