# Importação em Lote

Sou a porta por onde o conteúdo chega em volume. Existo para que o administrador não precise criar pergunta por pergunta quando tem um arquivo CSV ou JSON em mãos — e para garantir que nada entre no banco sem ser inspecionado antes.

> **Pertenço a:** [`funcionalidades/`](./README.md)

---

## O que acontece aqui

O fluxo é sempre em duas etapas: **preview** e **commit**.

No preview, o administrador envia o arquivo. O sistema detecta o formato, analisa cada item e devolve um relatório: quantos são novos, quantos são atualizações, quantos são duplicatas — sem gravar nada. O administrador vê o que vai acontecer antes de decidir.

No commit, o arquivo é enviado novamente com o comportamento escolhido para duplicatas. O sistema persiste e devolve o resultado final: quantos foram importados, atualizados, ignorados, e quais geraram erro.

Templates prontos para CSV e JSON estão disponíveis para download direto — o administrador não precisa adivinhar o formato esperado.

---

## Leitura contextual

O arquivo é reenviado no commit — não há cache entre as duas etapas. Preview e commit são operações independentes; o sistema não guarda estado entre elas.

O preview exibe no máximo 20 itens por padrão (`previewLimit`), mas processa o arquivo inteiro para calcular os totais do relatório.

A detecção de formato usa o `Content-Type` do upload; se ausente, infere pela extensão do nome do arquivo (`.csv` ou `.json`). Arquivos sem extensão e sem `Content-Type` reconhecível são rejeitados.

---

## 1. Endpoints

| Método | Rota | Função |
|--------|------|--------|
| `POST` | `/api/questions/import/preview` | Analisa o arquivo sem gravar (dry-run) |
| `POST` | `/api/questions/import/commit` | Executa a importação definitiva |
| `GET` | `/api/questions/import/template.json` | Download do template JSON |
| `GET` | `/api/questions/import/template.csv` | Download do template CSV |

Ambos os endpoints de importação requerem autenticação (`bearerAuth`).

---

## 2. Fluxo

1. Administrador faz upload do arquivo em `/preview`
2. O sistema detecta o formato pelo `Content-Type` ou extensão do arquivo
3. O parser correspondente (`CsvImportParser` ou `JsonImportParser`) analisa e classifica cada item como `NEW`, `UPDATE` ou `DUPLICATE`
4. O sistema devolve o preview com totais e os primeiros itens (até `previewLimit`)
5. Administrador revisa e escolhe o modo de duplicata: `IGNORE`, `UPDATE` ou `ALLOW`
6. Administrador reenvia o arquivo com o modo escolhido em `/commit`
7. O sistema persiste e devolve o resultado final com contadores e detalhes por item

---

## 3. Modos de deduplicação

| Modo | Comportamento |
|------|---------------|
| `IGNORE` | Duplicatas são puladas — apenas itens novos são importados |
| `UPDATE` | Duplicatas atualizam o registro existente |
| `ALLOW` | Duplicatas são inseridas como novos registros (permite repetição) |

---

## 4. Estrutura dos itens

Cada item do arquivo deve conter:

| Campo | Tipo | Regra |
|-------|------|-------|
| `question` | String | Texto da pergunta |
| `options` | Array | Exatamente 4 opções |
| `correctAnswer` | Integer | Índice da correta (0–3) |
| `points` | Integer | Pontuação base |
| `active` | Boolean | Estado inicial da pergunta |
| `categoryId` | Long | ID da categoria de destino |
| `imageUrl` | String | URL de imagem (opcional) |

**Código:** `QuestionImportController.java`, `QuestionImportPreviewService.java`, `QuestionImportService.java`, `service/questions/importer/`

---

## Exploração

- Curadoria manual de perguntas → [`banco-de-perguntas.md`](./banco-de-perguntas.md)
- Geração de perguntas por IA → [`integracao-openai.md`](./integracao-openai.md)
- Visão geral das funcionalidades → [`README.md`](./README.md)

---

*Importação em Lote — versão 1.0*
