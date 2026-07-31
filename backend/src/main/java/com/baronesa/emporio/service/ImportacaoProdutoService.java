package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.importacao.*;
import com.baronesa.emporio.dto.ProdutoRequest;
import com.baronesa.emporio.entity.Categoria;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.repository.CategoriaRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportacaoProdutoService {

    // Injetar o repositório de categorias
    private final CategoriaRepository categoriaRepository;

    // Injetar o serviço de produtos
    private final ProdutoService produtoService;

    // Injetar o repositório de produtos
    private final ProdutoRepository produtoRepository;

    // Mapeamento das colunas esperadas no template (case-insensitive e tolerante a acentos/espacos)
    private static final String[] COLUNA_CODIGO = {"codigo", "código", "cod", "codigo_interno", "código_interno", "id", "codigo produto", "código produto"};
    private static final String[] COLUNA_DESCRICAO = {"descricao", "descrição", "nome", "produto", "descricao_produto", "descrição_produto", "nome_produto", "item", "texto", "produto_descricao", "produto_descrição"};
    private static final String[] COLUNA_CUSTO = {"custo", "preco", "preço", "valor", "preco_custo", "preço_custo", "vl_custo", "valor_custo", "unitario", "unitário"};
    private static final String[] COLUNA_PRECO_VENDA = {"preco_venda", "preço_venda", "valor_venda", "vl_venda", "preco_final", "valor_final", "venda", "preco_de_venda"};
    private static final String[] COLUNA_UNIDADE = {"unidade", "unidade_medida", "unidade base", "un", "und", "medida", "tipo_unidade", "unidade_produto", "sigla_unidade"};
    private static final String[] COLUNA_ATIVO = {"ativo", "status", "situacao", "situação", "habilitado", "ativo_inativo", "status_ativo", "ind_ativo", "flag_ativo"};
    private static final String[] COLUNA_GRUPO = {"grupo", "categoria", "grupo_produto", "categoria_produto", "classificacao", "classificação", "tipo", "segmento", "area", "área", "setor", "fabricante", "marca"};
    private static final String[] COLUNA_NCM = {"ncm", "codigo_ncm", "código_ncm", "ncm_produto", "codigo_nomenclatura", "código_nomenclatura", "nomenclatura"};

    public ImportPreviewResponse gerarPreview(MultipartFile file) throws IOException {
        if (!file.getOriginalFilename().toLowerCase().endsWith(".xls") &&
            !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Apenas arquivos XLS/XLSX são suportados");
        }

        Workbook workbook = null;
        try (InputStream inputStream = file.getInputStream()) {
            if (file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(inputStream);
            } else {
                workbook = new XSSFWorkbook(inputStream);
            }

            Sheet sheet = workbook.getSheetAt(0); // Assume primeira planilha
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) {
                return ImportPreviewResponse.builder()
                        .total(0)
                        .validos(0)
                        .duplicadosInternos(0)
                        .invalidos(0)
                        .linhasValidas(new ArrayList<>())
                        .exemploInvalido(null)
                        .categoriasDetectadas(new ArrayList<>())
                        .build();
            }

            // Ler cabeçalho
            Row headerRow = rowIterator.next();

            // Mapear colunas usando os arrays de variações
            Integer colunaCodigoIndex = encontrarIndiceColuna(headerRow, COLUNA_CODIGO);
            Integer colunaDescricaoIndex = encontrarIndiceColuna(headerRow, COLUNA_DESCRICAO);
            Integer colunaCustoIndex = encontrarIndiceColuna(headerRow, COLUNA_CUSTO);
            Integer colunaVendaIndex = encontrarIndiceColuna(headerRow, COLUNA_PRECO_VENDA);
            Integer colunaUnidadeIndex = encontrarIndiceColuna(headerRow, COLUNA_UNIDADE);
            Integer colunaAtivoIndex = encontrarIndiceColuna(headerRow, COLUNA_ATIVO);
            Integer colunaGrupoIndex = encontrarIndiceColuna(headerRow, COLUNA_GRUPO);
            Integer colunaNcmIndex = encontrarIndiceColuna(headerRow, COLUNA_NCM);

            List<ProdutoPreviewItem> todasLinhasValidas = new ArrayList<>(); // Armazenar todas as linhas válidas para processamento posterior
            int totalLinhas = 0;
            int duplicadosInternos = 0;
            int invalidos = 0;
            ExemploInvalido exemploInvalido = null;
            Set<String> codigosEncontrados = new HashSet<>();
            Set<String> codigosDuplicados = new HashSet<>();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                totalLinhas++;

                ProdutoPreviewItem item = converterLinhaParaProdutoPreviewComIndices(row, colunaCodigoIndex, colunaDescricaoIndex,
                    colunaCustoIndex, colunaVendaIndex, colunaUnidadeIndex, colunaAtivoIndex, colunaGrupoIndex, colunaNcmIndex);

                if (item == null) {
                    invalidos++;
                    if (exemploInvalido == null) {
                        exemploInvalido = ExemploInvalido.builder()
                                .linha(totalLinhas)
                                .mensagem("Linha vazia ou sem dados válidos")
                                .build();
                    }
                    continue;
                }

                // Validar campos obrigatórios
                List<String> erros = validarItem(item);
                if (!erros.isEmpty()) {
                    invalidos++;
                    if (exemploInvalido == null) {
                        exemploInvalido = ExemploInvalido.builder()
                                .linha(totalLinhas)
                                .mensagem(String.join(", ", erros))
                                .build();
                    }
                    continue;
                }

                // Verificar duplicidade
                if (codigosEncontrados.contains(item.getCodigoInterno())) {
                    if (!codigosDuplicados.contains(item.getCodigoInterno())) {
                        duplicadosInternos++;
                        codigosDuplicados.add(item.getCodigoInterno());
                    }
                } else {
                    codigosEncontrados.add(item.getCodigoInterno());
                }

                // Adicionar à lista de válidos se não for duplicado
                if (!codigosDuplicados.contains(item.getCodigoInterno())) {
                    todasLinhasValidas.add(item);
                }
            }

            int validos = totalLinhas - invalidos - duplicadosInternos;

            // Criar mapa de contagem de categorias detectadas
            Map<String, Long> contagemCategorias = todasLinhasValidas.stream()
                .filter(item -> item.getGrupo() != null && !item.getGrupo().trim().isEmpty())
                .collect(Collectors.groupingBy(
                    ProdutoPreviewItem::getGrupo,
                    Collectors.counting()
                ));

            // Consultar categorias existentes no banco de dados
            List<CategoriaDetectada> categoriasDetectadas = new ArrayList<>();
            for (Map.Entry<String, Long> entry : contagemCategorias.entrySet()) {
                String nomeGrupo = entry.getKey();
                Long contagem = entry.getValue();

                // Procurar categoria existente (case-insensitive e ignoring accents)
                List<Categoria> categoriasExistentes = categoriaRepository.findByNomeContainingIgnoreCase(nomeGrupo);
                Categoria categoriaEncontrada = null;

                // Verificar se alguma categoria coincide exatamente (desconsiderando acentos e maiúsculas)
                for (Categoria cat : categoriasExistentes) {
                    if (normalizeString(cat.getNome()).equalsIgnoreCase(normalizeString(nomeGrupo))) {
                        categoriaEncontrada = cat;
                        break;
                    }
                }

                CategoriaDetectada categoriaDetectada = CategoriaDetectada.builder()
                    .nome(nomeGrupo)
                    .existe(categoriaEncontrada != null)
                    .categoriaId(categoriaEncontrada != null ? categoriaEncontrada.getId() : null)
                    .contagem(Math.toIntExact(contagem))
                    .build();

                categoriasDetectadas.add(categoriaDetectada);
            }

            // Atualizar os itens com os IDs de categoria encontrados
            for (ProdutoPreviewItem item : todasLinhasValidas) {
                if (item.getGrupo() != null && !item.getGrupo().trim().isEmpty()) {
                    CategoriaDetectada categoriaDetectada = categoriasDetectadas.stream()
                        .filter(cat -> cat.getNome().equals(item.getGrupo()))
                        .findFirst()
                        .orElse(null);

                    if (categoriaDetectada != null && categoriaDetectada.getExiste()) {
                        item.setCategoriaId(categoriaDetectada.getCategoriaId());
                    }
                }
            }

            // Ordenar todas as linhas válidas por nome (ascendente) antes de limitar
            todasLinhasValidas.sort((a, b) -> {
                if (a.getNome() == null && b.getNome() == null) return 0;
                if (a.getNome() == null) return 1;
                if (b.getNome() == null) return -1;
                return a.getNome().compareToIgnoreCase(b.getNome());
            });

            // Limitar linhas válidas a 50 para o preview
            List<ProdutoPreviewItem> linhasValidas = todasLinhasValidas;
            if (linhasValidas.size() > 50) {
                linhasValidas = linhasValidas.subList(0, 50);
            }

            return ImportPreviewResponse.builder()
                    .total(totalLinhas)
                    .validos(validos)
                    .duplicadosInternos(duplicadosInternos)
                    .invalidos(invalidos)
                    .linhasValidas(linhasValidas)
                    .exemploInvalido(exemploInvalido)
                    .categoriasDetectadas(categoriasDetectadas)
                    .build();
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    // Método auxiliar para normalizar strings (remover acentos e converter para minúsculas)
    private String normalizeString(String input) {
        if (input == null) {
            return null;
        }
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "") // Remove acentos
                .toLowerCase()
                .trim();
    }

    private Map<String, Integer> mapearColunas(Row headerRow) {
        Map<String, Integer> colunasMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String columnName = cell.getStringCellValue();
                if (columnName != null) {
                    // Normalizar o nome da coluna para comparação
                    String normalizedColumnName = normalizeColumnName(columnName);
                    colunasMap.put(normalizedColumnName, i);
                }
            }
        }
        return colunasMap;
    }

    // Método para normalizar nomes de colunas, removendo acentos, espaços e convertendo para minúsculas
    private String normalizeColumnName(String columnName) {
        if (columnName == null) {
            return null;
        }
        return java.text.Normalizer.normalize(columnName, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "") // Remove acentos
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", "_") // Substitui espaços por underscores
                .replaceAll("[^a-z0-9_]", ""); // Remove caracteres especiais
    }

    // Método para encontrar a coluna correspondente nos arrays de variações
    private Integer encontrarIndiceColuna(Row headerRow, String[] colunasEsperadas) {
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String cellValue = cell.getStringCellValue();
                if (cellValue != null) {
                    String normalizedValue = normalizeColumnName(cellValue);
                    for (String colunaEsperada : colunasEsperadas) {
                        if (normalizedValue != null && normalizedValue.equals(colunaEsperada.replace(" ", "_").replaceAll("[^a-z0-9_]", ""))) {
                            return i;
                        }
                    }
                }
            }
        }
        return null;
    }

    private ProdutoPreviewItem converterLinhaParaProdutoPreviewComIndices(Row row,
            Integer colunaCodigoIndex, Integer colunaDescricaoIndex, Integer colunaCustoIndex,
            Integer colunaVendaIndex, Integer colunaUnidadeIndex, Integer colunaAtivoIndex, Integer colunaGrupoIndex, Integer colunaNcmIndex) {
        try {
            String codigo = getCellValueAsString(row, colunaCodigoIndex);
            if (codigo == null || codigo.trim().isEmpty()) {
                return null; // Linha inválida se não tiver código
            }

            String descricao = getCellValueAsString(row, colunaDescricaoIndex);
            String custo = getCellValueAsString(row, colunaCustoIndex);
            String venda = getCellValueAsString(row, colunaVendaIndex);
            String unidade = getCellValueAsString(row, colunaUnidadeIndex);
            String ativo = getCellValueAsString(row, colunaAtivoIndex);
            String grupo = getCellValueAsString(row, colunaGrupoIndex);
            String ncm = getCellValueAsString(row, colunaNcmIndex);

            // Normalizar números: substituir vírgula por ponto, garantir duas casas decimais
            String precoCusto = normalizarNumero(custo != null ? custo : venda);
            String precoVenda = normalizarNumero(venda != null ? venda : custo);

            // Normalizar unidade
            String unidadeMedida = unidade != null ? unidade.toUpperCase() : "UN";
            String unidadeBase = unidade != null ? obterUnidadeBase(unidade) : "UNIDADE";

            // Normalizar booleano ativo (1 = true, outros = false)
            Boolean ativoBoolean = "1".equals(ativo) || "true".equalsIgnoreCase(ativo) || "sim".equalsIgnoreCase(ativo);

            // Não definir categoriaId diretamente, será definido posteriormente após consulta
            Long categoriaId = null;

            return ProdutoPreviewItem.builder()
                    .nome(descricao)
                    .descricao(descricao)
                    .codigoInterno(codigo)
                    .precoCusto(precoCusto)
                    .precoVenda(precoVenda)
                    .margemLucro(100) // default simples
                    .tipoPrecificacao("SIMPLES")
                    .unidadeMedida(unidadeMedida)
                    .unidadeBase(unidadeBase)
                    .tipoCalculoMargem("SOBRE_CUSTO")
                    .ativo(ativoBoolean)
                    .ncm(ncm)
                    .grupo(grupo)
                    .categoriaId(categoriaId)
                    .build();
        } catch (Exception e) {
            log.error("Erro ao converter linha para ProdutoPreviewItem: ", e);
            return null;
        }
    }

    // Manter o método original para compatibilidade, mas com o novo mapeamento
    private ProdutoPreviewItem converterLinhaParaProdutoPreview(Row row, Map<String, Integer> colunasMap) {
        // Encontrar os índices baseados nos nomes normalizados das colunas
        Integer codigoIndex = null;
        Integer descricaoIndex = null;
        Integer custoIndex = null;
        Integer vendaIndex = null;
        Integer unidadeIndex = null;
        Integer ativoIndex = null;
        Integer grupoIndex = null;
        Integer ncmIndex = null;

        for (Map.Entry<String, Integer> entry : colunasMap.entrySet()) {
            String normalizedColName = entry.getKey();
            Integer colIndex = entry.getValue();

            if (matchesAny(normalizedColName, COLUNA_CODIGO)) {
                codigoIndex = colIndex;
            } else if (matchesAny(normalizedColName, COLUNA_DESCRICAO)) {
                descricaoIndex = colIndex;
            } else if (matchesAny(normalizedColName, COLUNA_CUSTO)) {
                custoIndex = colIndex;
            } else if (matchesAny(normalizedColName, COLUNA_PRECO_VENDA)) {
                vendaIndex = colIndex;
            } else if (matchesAny(normalizedColName, COLUNA_UNIDADE)) {
                unidadeIndex = colIndex;
            } else if (matchesAny(normalizedColName, COLUNA_ATIVO)) {
                ativoIndex = colIndex;
            } else if (matchesAny(normalizedColName, COLUNA_GRUPO)) {
                grupoIndex = colIndex;
            } else if (matchesAny(normalizedColName, COLUNA_NCM)) {
                ncmIndex = colIndex;
            }
        }

        return converterLinhaParaProdutoPreviewComIndices(row, codigoIndex, descricaoIndex, custoIndex,
                vendaIndex, unidadeIndex, ativoIndex, grupoIndex, ncmIndex);
    }

    // Método auxiliar para verificar se um nome de coluna corresponde a alguma das variações esperadas
    private boolean matchesAny(String columnName, String[] expectedValues) {
        if (columnName == null) {
            return false;
        }
        for (String expectedValue : expectedValues) {
            String normalizedExpected = expectedValue.replace(" ", "_").replaceAll("[^a-z0-9_]", "");
            if (columnName.equals(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }

    private String getCellValueAsString(Row row, Integer columnIndex) {
        if (columnIndex == null || columnIndex < 0) {
            return null;
        }

        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Verificar se é um número inteiro
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
            case ERROR:
            default:
                return null;
        }
    }

    private String normalizarNumero(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return "0.00";
        }

        // Substituir vírgula por ponto
        valor = valor.replace(',', '.');

        try {
            BigDecimal decimal = new BigDecimal(valor);
            // Formatar com 2 casas decimais
            return String.format("%.2f", decimal.doubleValue());
        } catch (NumberFormatException e) {
            log.warn("Não foi possível converter '{}' para número", valor);
            return "0.00";
        }
    }

    private String obterUnidadeBase(String unidade) {
        if (unidade == null) return "UNIDADE";

        String unidadeUpper = unidade.toUpperCase().trim();

        // Mapear unidades comuns para unidades base
        if ("ML".equals(unidadeUpper) || "MILILITRO".equals(unidadeUpper)) {
            return "MILILITRO";
        } else if ("G".equals(unidadeUpper) || "GRAMA".equals(unidadeUpper)) {
            return "GRAMA";
        } else {
            return "UNIDADE"; // fallback padrão
        }
    }

    private List<String> validarItem(ProdutoPreviewItem item) {
        List<String> erros = new ArrayList<>();

        if (item.getCodigoInterno() == null || item.getCodigoInterno().trim().isEmpty()) {
            erros.add("Código interno ausente");
        }

        if (item.getPrecoCusto() == null) {
            erros.add("Preço de custo ausente");
        } else {
            try {
                Double.parseDouble(item.getPrecoCusto());
            } catch (NumberFormatException e) {
                erros.add("Preço de custo inválido");
            }
        }

        if (item.getPrecoVenda() == null) {
            erros.add("Preço de venda ausente");
        } else {
            try {
                Double.parseDouble(item.getPrecoVenda());
            } catch (NumberFormatException e) {
                erros.add("Preço de venda inválido");
            }
        }

        return erros;
    }

    /**
     * Confirma a importação de produtos a partir de um arquivo XLS/XLSX
     *
     * @param file Arquivo XLS/XLSX contendo os produtos
     * @return ConfirmacaoImportacaoResponse com resumo da operação
     */
    public ConfirmacaoImportacaoResponse confirmarImportacao(MultipartFile file) throws IOException {
        if (!file.getOriginalFilename().toLowerCase().endsWith(".xls") &&
            !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Apenas arquivos XLS/XLSX são suportados");
        }

        // Verificar se já existem produtos na tabela - se sim, impedir a importação
        long totalProdutosExistentes = produtoRepository.count();
        if (totalProdutosExistentes > 0) {
            throw new IllegalStateException("A importação não é permitida porque já existem produtos cadastrados no sistema. A importação só pode ser feita com a tabela de produtos vazia.");
        }

        // Reutilizar a lógica de leitura do arquivo do método de preview
        Workbook workbook = null;
        try (InputStream inputStream = file.getInputStream()) {
            if (file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(inputStream);
            } else {
                workbook = new XSSFWorkbook(inputStream);
            }

            Sheet sheet = workbook.getSheetAt(0); // Assume primeira planilha
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) {
                return ConfirmacaoImportacaoResponse.builder()
                        .total(0)
                        .processados(0)
                        .criadas(0)
                        .ignoradasDuplicadas(0)
                        .erros(0)
                        .categoriasCriadas(new ArrayList<>())
                        .amostrasErro(new ArrayList<>())
                        .build();
            }

            // Ler cabeçalho
            Row headerRow = rowIterator.next();

            // Mapear colunas usando os arrays de variações
            Integer colunaCodigoIndex = encontrarIndiceColuna(headerRow, COLUNA_CODIGO);
            Integer colunaDescricaoIndex = encontrarIndiceColuna(headerRow, COLUNA_DESCRICAO);
            Integer colunaCustoIndex = encontrarIndiceColuna(headerRow, COLUNA_CUSTO);
            Integer colunaVendaIndex = encontrarIndiceColuna(headerRow, COLUNA_PRECO_VENDA);
            Integer colunaUnidadeIndex = encontrarIndiceColuna(headerRow, COLUNA_UNIDADE);
            Integer colunaAtivoIndex = encontrarIndiceColuna(headerRow, COLUNA_ATIVO);
            Integer colunaGrupoIndex = encontrarIndiceColuna(headerRow, COLUNA_GRUPO);
            Integer colunaNcmIndex = encontrarIndiceColuna(headerRow, COLUNA_NCM);

            List<ProdutoPreviewItem> todosItens = new ArrayList<>(); // Armazenar todos os itens para processamento
            int totalLinhas = 0;
            int duplicadosInternos = 0;
            int invalidos = 0;
            List<AmostraErro> amostrasErro = new ArrayList<>();
            Set<String> codigosEncontrados = new HashSet<>();
            Set<String> codigosDuplicados = new HashSet<>();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                totalLinhas++;

                ProdutoPreviewItem item = converterLinhaParaProdutoPreviewComIndices(row, colunaCodigoIndex, colunaDescricaoIndex,
                    colunaCustoIndex, colunaVendaIndex, colunaUnidadeIndex, colunaAtivoIndex, colunaGrupoIndex, colunaNcmIndex);

                if (item == null) {
                    invalidos++;
                    if (amostrasErro.size() < 5) { // Limitar amostras de erro
                        amostrasErro.add(AmostraErro.builder()
                                .linha(totalLinhas)
                                .mensagem("Linha vazia ou sem dados válidos")
                                .build());
                    }
                    continue;
                }

                // Validar campos obrigatórios
                List<String> erros = validarItem(item);
                if (!erros.isEmpty()) {
                    invalidos++;
                    if (amostrasErro.size() < 5) { // Limitar amostras de erro
                        amostrasErro.add(AmostraErro.builder()
                                .linha(totalLinhas)
                                .mensagem(String.join(", ", erros))
                                .build());
                    }
                    continue;
                }

                // Verificar duplicidade
                if (codigosEncontrados.contains(item.getCodigoInterno())) {
                    if (!codigosDuplicados.contains(item.getCodigoInterno())) {
                        duplicadosInternos++;
                        codigosDuplicados.add(item.getCodigoInterno());
                    }
                } else {
                    codigosEncontrados.add(item.getCodigoInterno());
                }

                // Adicionar à lista de itens para processamento
                todosItens.add(item);
            }

            // Processar os itens para criar produtos
            int processados = 0;
            int criadas = 0;
            int ignoradasDuplicadas = 0;
            int erros = 0;

            Set<Long> idsCategoriasCriadas = new HashSet<>();
            List<CategoriaDetectada> categoriasCriadas = new ArrayList<>();

            // Criar mapa de categorias existentes para otimizar buscas
            Map<String, Categoria> categoriasExistentes = new HashMap<>();
            List<Categoria> todasCategorias = categoriaRepository.findAll();
            for (Categoria cat : todasCategorias) {
                categoriasExistentes.put(normalizeString(cat.getNome()), cat);
            }

            // Criar categoria padrão se necessário
            Categoria categoriaPadrao = criarOuObterCategoriaPadrao();

            // Contadores para categorias criadas
            Map<String, Integer> contagemCategorias = new HashMap<>();

            for (ProdutoPreviewItem item : todosItens) {
                processados++;

                try {
                    // Resolver categoria
                    String grupo = item.getGrupo();
                    Long categoriaId = null;

                    if (grupo != null && !grupo.trim().isEmpty()) {
                        String nomeGrupoNormalizado = normalizeString(grupo);

                        // Verificar se já existe no mapa
                        Categoria catExistente = categoriasExistentes.get(nomeGrupoNormalizado);
                        if (catExistente != null) {
                            categoriaId = catExistente.getId();

                            // Atualizar contagem
                            contagemCategorias.merge(catExistente.getNome(), 1, Integer::sum);
                        } else {
                            // Criar nova categoria
                            Categoria novaCategoria = Categoria.builder()
                                    .nome(grupo)
                                    .icone(null)
                                    .cover(null)
                                    .build();
                            Categoria categoriaSalva = categoriaRepository.save(novaCategoria);

                            categoriasExistentes.put(nomeGrupoNormalizado, categoriaSalva);
                            categoriaId = categoriaSalva.getId();

                            if (!idsCategoriasCriadas.contains(categoriaSalva.getId())) {
                                idsCategoriasCriadas.add(categoriaSalva.getId());
                                categoriasCriadas.add(CategoriaDetectada.builder()
                                        .nome(categoriaSalva.getNome())
                                        .existe(false) // Falso porque ela foi criada agora
                                        .categoriaId(categoriaSalva.getId())
                                        .contagem(1) // Inicializado com 1
                                        .build());

                                contagemCategorias.put(categoriaSalva.getNome(), 1);
                            } else {
                                // Apenas incrementar a contagem se já existia
                                contagemCategorias.merge(categoriaSalva.getNome(), 1, Integer::sum);
                            }
                        }
                    } else {
                        // Usar categoria padrão se grupo estiver vazio
                        categoriaId = categoriaPadrao.getId();

                        // Atualizar contagem para a categoria padrão
                        contagemCategorias.merge(categoriaPadrao.getNome(), 1, Integer::sum);
                    }

                    // Verificar se produto com este código interno já existe
                    boolean produtoExiste = produtoService.existePorCodigoInterno(item.getCodigoInterno());

                    if (produtoExiste) {
                        // Produto já existe, incrementar contador de ignorados
                        ignoradasDuplicadas++;
                        if (!codigosDuplicados.contains(item.getCodigoInterno())) {
                            codigosDuplicados.add(item.getCodigoInterno());
                        }
                    } else {
                        // Produto não existe, criar novo
                        criarProdutoAPartirDoItem(item, categoriaId);
                        criadas++;
                    }

                } catch (Exception e) {
                    erros++;
                    if (amostrasErro.size() < 5) {
                        amostrasErro.add(AmostraErro.builder()
                                .linha(processados)
                                .mensagem(e.getMessage())
                                .build());
                    }
                }
            }

            // Atualizar contagem nas categorias detectadas
            for (CategoriaDetectada cat : categoriasCriadas) {
                cat.setContagem(contagemCategorias.getOrDefault(cat.getNome(), 0));
            }

            // Calcular total processado
            int totalValidos = totalLinhas - invalidos - duplicadosInternos;

            return ConfirmacaoImportacaoResponse.builder()
                    .total(totalLinhas)
                    .processados(processados)
                    .criadas(criadas)
                    .ignoradasDuplicadas(ignoradasDuplicadas)
                    .erros(erros)
                    .categoriasCriadas(categoriasCriadas)
                    .amostrasErro(amostrasErro)
                    .build();
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    /**
     * Cria ou obtém a categoria padrão "Geral" para uso quando grupo está vazio
     */
    private Categoria criarOuObterCategoriaPadrao() {
        String nomeCategoriaPadrao = "Geral";

        // Tentar encontrar categoria existente com nome "Geral"
        List<Categoria> categorias = categoriaRepository.findByNomeContainingIgnoreCase(nomeCategoriaPadrao);
        for (Categoria cat : categorias) {
            if (normalizeString(cat.getNome()).equals(normalizeString(nomeCategoriaPadrao))) {
                return cat;
            }
        }

        // Se não encontrou, criar nova categoria padrão
        Categoria categoriaPadrao = Categoria.builder()
                .nome(nomeCategoriaPadrao)
                .icone(null)
                .cover(null)
                .build();

        return categoriaRepository.save(categoriaPadrao);
    }

    /**
     * Cria produto a partir de item de preview
     */
    private void criarProdutoAPartirDoItem(ProdutoPreviewItem item, Long categoriaId) {
        // Criar um ProdutoRequest a partir do item de preview
        // Converter strings para enums de forma segura
        com.baronesa.emporio.enums.TipoPrecificacao tipoPrecificacao = converteTipoPrecificacao(item.getTipoPrecificacao());
        com.baronesa.emporio.enums.UnidadeMedida unidadeMedida = converteUnidadeMedida(item.getUnidadeMedida());
        com.baronesa.emporio.enums.UnidadeBase unidadeBase = converteUnidadeBase(item.getUnidadeBase());

        ProdutoRequest produtoRequest = ProdutoRequest.builder()
                .nome(item.getNome())
                .descricao(item.getDescricao())
                .codigoInterno(item.getCodigoInterno())
                .categoriaId(categoriaId)
                .tipoPrecificacao(tipoPrecificacao)
                .unidadeMedida(unidadeMedida)
                .unidadeBase(unidadeBase)
                .ativo(true)
                .ncm(item.getNcm())
                .precoCusto(new BigDecimal(item.getPrecoCusto() != null ? item.getPrecoCusto() : "0.00"))
                .precoVenda(new BigDecimal(item.getPrecoVenda() != null ? item.getPrecoVenda() : item.getPrecoCusto() != null ? item.getPrecoCusto() : "0.00"))
                .build();

        // Salvar o produto usando o serviço
        produtoService.criar(produtoRequest);

        log.info("Produto criado com sucesso: código: {}, nome: {}, categoriaId: {}",
                item.getCodigoInterno(), item.getNome(), categoriaId);
    }

    /**
     * Converte string para TipoPrecificacao de forma segura
     */
    private com.baronesa.emporio.enums.TipoPrecificacao converteTipoPrecificacao(String tipoStr) {
        if (tipoStr == null) {
            return com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES; // valor padrão
        }

        for (com.baronesa.emporio.enums.TipoPrecificacao tipo : com.baronesa.emporio.enums.TipoPrecificacao.values()) {
            if (tipo.name().equalsIgnoreCase(tipoStr)) {
                return tipo;
            }
        }
        return com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES; // valor padrão
    }

    /**
     * Converte string para UnidadeMedida de forma segura
     */
    private com.baronesa.emporio.enums.UnidadeMedida converteUnidadeMedida(String unidadeStr) {
        if (unidadeStr == null) {
            return com.baronesa.emporio.enums.UnidadeMedida.UN; // valor padrão
        }

        for (com.baronesa.emporio.enums.UnidadeMedida unidade : com.baronesa.emporio.enums.UnidadeMedida.values()) {
            if (unidade.name().equalsIgnoreCase(unidadeStr) ||
                unidade.getSigla().equalsIgnoreCase(unidadeStr)) {
                return unidade;
            }
        }
        return com.baronesa.emporio.enums.UnidadeMedida.UN; // valor padrão
    }

    /**
     * Converte string para UnidadeBase de forma segura
     */
    private com.baronesa.emporio.enums.UnidadeBase converteUnidadeBase(String unidadeStr) {
        if (unidadeStr == null) {
            return com.baronesa.emporio.enums.UnidadeBase.UNIDADE; // valor padrão
        }

        for (com.baronesa.emporio.enums.UnidadeBase unidade : com.baronesa.emporio.enums.UnidadeBase.values()) {
            if (unidade.name().equalsIgnoreCase(unidadeStr) ||
                unidade.getSigla().equalsIgnoreCase(unidadeStr)) {
                return unidade;
            }
        }
        return com.baronesa.emporio.enums.UnidadeBase.UNIDADE; // valor padrão
    }

}
