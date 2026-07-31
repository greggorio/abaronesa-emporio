package com.baronesa.emporio.controller;

import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.nfe.dto.DanfceModel;
import com.baronesa.emporio.nfe.service.DanfcePdfGeneratorService;
import com.baronesa.emporio.nfe.service.NfceParserService;
import com.baronesa.emporio.repository.NfeRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller REST para operações específicas do DANFCE (Documento Auxiliar da NFC-e).
 *
 * Endpoints disponíveis:
 * - Download de PDF do DANFCE
 * - Visualização HTML do DANFCE
 * - Estatísticas e Dados
 *
 * @author Sistema Loja (Ported to Bares)
 * @since 2025-01-31
 */
@Slf4j
@RestController
@RequestMapping("/api/danfce")
@RequiredArgsConstructor
public class DanfceController {

    private final NfeRepository nfeRepository;
    private final NfceParserService nfceParserService;
    private final DanfcePdfGeneratorService danfcePdfGenerator;
    private final PagamentoRepository pagamentoRepository;

    /**
     * Download do PDF do DANFCE
     * GET /api/danfce/{id}/pdf
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadDanfcePdf(@PathVariable Long id,
                                               @RequestParam(value = "modo", defaultValue = "TERMICA_80MM") String modo) {
        try {
            log.info("Solicitação de download DANFCE PDF - ID: {}, Modo: {}", id, modo);

            // Buscar NFC-e
            Optional<NfeModel> nfeOpt = nfeRepository.findById(id);
            if (!nfeOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(criarRespostaErro("NFC-e não encontrada com ID: " + id));
            }

            NfeModel nfe = nfeOpt.get();

            // Validar se é NFC-e
            if (nfe.getModelo() != null && nfe.getModelo() != 65) {
                log.warn("Documento ID {} não é NFC-e (modelo: {})", id, nfe.getModelo());
            }

            // Converter XML para modelo DANFCE
            DanfceModel danfce = nfceParserService.parseNfceXml(nfe);

            // Determinar modo de impressão
            DanfcePdfGeneratorService.ModoImpressao modoImpressao =
                    obterModoImpressao(modo);

            // Gerar PDF
            byte[] pdfBytes = danfcePdfGenerator.generateDanfcePdf(danfce, modoImpressao);

            // Preparar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String dataEmissaoStr = nfe.getDataEmissao() != null ? 
                nfe.getDataEmissao().format(DateTimeFormatter.ofPattern("ddMMyyyy")) : "00000000";
            
            headers.setContentDispositionFormData("attachment",
                    String.format("danfce_%s_%s.pdf",
                            nfe.getNumero() != null ? nfe.getNumero() : "S_N",
                            dataEmissaoStr));
            headers.setContentLength(pdfBytes.length);

            log.info("DANFCE PDF gerado com sucesso - ID: {}, Tamanho: {} bytes", id, pdfBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Erro ao gerar DANFCE PDF - ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao gerar PDF do DANFCE: " + e.getMessage()));
        }
    }

    /**
     * Visualização HTML do DANFCE
     * GET /api/danfce/{id}/html
     */
    @GetMapping("/{id}/html")
    public ResponseEntity<?> visualizarDanfceHtml(@PathVariable Long id) {
        try {
            log.info("Solicitação de visualização DANFCE HTML - ID: {}", id);

            // Buscar NFC-e
            Optional<NfeModel> nfeOpt = nfeRepository.findById(id);
            if (!nfeOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(criarRespostaErro("NFC-e não encontrada com ID: " + id));
            }

            NfeModel nfe = nfeOpt.get();

            // Converter XML para modelo DANFCE
            DanfceModel danfce = nfceParserService.parseNfceXml(nfe);

            // Gerar HTML
            String htmlContent = danfcePdfGenerator.generateDanfceHtml(danfce);

            log.info("DANFCE HTML gerado com sucesso - ID: {}", id);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(htmlContent);

        } catch (Exception e) {
            log.error("Erro ao gerar DANFCE HTML - ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao gerar HTML do DANFCE: " + e.getMessage()));
        }
    }

    /**
     * Informações sobre capacidades do DANFCE
     * GET /api/danfce/info
     */
    @GetMapping("/info")
    public ResponseEntity<?> obterInformacoesDanfce() {
        try {
            log.info("Solicitação de informações DANFCE");

            Map<String, Object> info = new HashMap<>();

            // Informações do gerador PDF
            info.putAll(danfcePdfGenerator.obterInformacoesDanfce());

            // Modos de impressão suportados
            info.put("modosImpressao", DanfcePdfGeneratorService.ModoImpressao.values());

            return ResponseEntity.ok(criarRespostaSucesso("Informações DANFCE", info));

        } catch (Exception e) {
            log.error("Erro ao obter informações DANFCE", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao obter informações: " + e.getMessage()));
        }
    }

    /**
     * Lista de NFC-e disponíveis para DANFCE
     * GET /api/danfce/lista
     */
    @GetMapping("/lista")
    public ResponseEntity<?> listarNfceDisponiveis(@RequestParam(value = "page", defaultValue = "0") int page,
                                                   @RequestParam(value = "size", defaultValue = "20") int size,
                                                   @RequestParam(value = "status", required = false) String status) {
        try {
            log.info("Solicitação de lista NFC-e - Page: {}, Size: {}, Status: {}", page, size, status);

            // Buscar NFC-e (modelo 65) com paginação
            Pageable pageable = PageRequest.of(page, size);

            Page<NfeModel> nfcePage;

            if (status != null && !status.trim().isEmpty()) {
                nfcePage = nfeRepository.findByModeloAndStatusOrderByDataEmissaoDesc(65, status.toUpperCase(), pageable);
            } else {
                nfcePage = nfeRepository.findByModeloOrderByDataEmissaoDesc(65, pageable);
            }

            // Preparar resposta
            Map<String, Object> response = new HashMap<>();
            response.put("content", nfcePage.getContent());
            response.put("totalElements", nfcePage.getTotalElements());
            response.put("totalPages", nfcePage.getTotalPages());
            response.put("currentPage", page);
            response.put("size", size);

            return ResponseEntity.ok(criarRespostaSucesso("Lista de NFC-e", response));

        } catch (Exception e) {
            log.error("Erro ao listar NFC-e", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao listar NFC-e: " + e.getMessage()));
        }
    }

    /**
     * Dados de uma NFC-e específica em formato resumido
     * GET /api/danfce/{id}/dados
     */
    @GetMapping("/{id}/dados")
    public ResponseEntity<?> obterDadosNfce(@PathVariable Long id) {
        try {
            log.info("Solicitação de dados NFC-e - ID: {}", id);

            // Buscar NFC-e
            Optional<NfeModel> nfeOpt = nfeRepository.findById(id);
            if (!nfeOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(criarRespostaErro("NFC-e não encontrada com ID: " + id));
            }

            NfeModel nfe = nfeOpt.get();

            // Converter para modelo DANFCE
            DanfceModel danfce = nfceParserService.parseNfceXml(nfe);

            // Preparar dados resumidos
            Map<String, Object> dados = new HashMap<>();
            dados.put("id", nfe.getId());
            dados.put("numero", danfce.getNumero());
            dados.put("serie", danfce.getSerie());
            dados.put("chaveAcesso", danfce.getChaveAcesso());
            dados.put("dataEmissao", danfce.getDataEmissao());
            dados.put("valorTotal", danfce.getValorTotalNota());
            dados.put("status", nfe.getStatus());
            dados.put("protocolo", danfce.getProtocoloAutorizacao());
            dados.put("homologacao", danfce.isHomologacao());
            dados.put("quantidadeItens", danfce.getProdutos().size());
            dados.put("emitente", danfce.getNomeFantasiaEmitente());
            dados.put("consumidor", danfce.getNomeDestinatario());

            return ResponseEntity.ok(criarRespostaSucesso("Dados da NFC-e", dados));

        } catch (Exception e) {
            log.error("Erro ao obter dados NFC-e - ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao obter dados: " + e.getMessage()));
        }
    }

    /**
     * Consulta rápida de NFC-e gerada para um pagamento
     * GET /api/danfce/pagamento/{pagamentoId}
     */
    @GetMapping("/pagamento/{pagamentoId}")
    public ResponseEntity<?> buscarPorPagamento(@PathVariable Long pagamentoId) {
        try {
            Optional<NfeModel> nfeOpt = nfeRepository.findByIdVenda(pagamentoId);
            Optional<Pagamento> pagamentoOpt = pagamentoRepository.findById(pagamentoId);

            // Sempre preparar info de cliente, mesmo se ainda não houver NFe/NFCe
            Map<String, Object> clienteInfo = new LinkedHashMap<>();
            if (pagamentoOpt.isPresent()) {
                Pagamento pagamento = pagamentoOpt.get();
                var convidado = pagamento.getSessaoConvidado();
                if (convidado != null && convidado.getUsuario() != null && convidado.getUsuario().getPerfilCliente() != null) {
                    var perfil = convidado.getUsuario().getPerfilCliente();
                    clienteInfo.put("clienteTipoPessoa", perfil.getTipoPessoa() != null ? perfil.getTipoPessoa().name() : null);
                    clienteInfo.put("clienteCpf", perfil.getCpf());
                    clienteInfo.put("clienteCnpj", perfil.getCnpj());
                }
            }

            if (nfeOpt.isEmpty()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("exists", false);
                payload.putAll(clienteInfo);
                return ResponseEntity.ok(criarRespostaSucesso("Nenhuma NFC-e encontrada", payload));
            }

            NfeModel nfe = nfeOpt.get();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", nfe.getId());
            info.put("numero", nfe.getNumero());
            info.put("status", nfe.getStatus());
            info.put("chaveAcesso", nfe.getChaveAcesso());
            info.put("ambiente", nfe.getAmbiente());
            info.put("dataEmissao", nfe.getDataEmissao() != null ? nfe.getDataEmissao().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            info.put("protocolo", nfe.getProtocolo());
            info.put("valorTotal", nfe.getValorTotal());
            info.put("modelo", nfe.getModelo());
            info.putAll(clienteInfo);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("exists", true);
            payload.put("nfe", info);

            return ResponseEntity.ok(criarRespostaSucesso("NFC-e encontrada para o pagamento", payload));

        } catch (Exception e) {
            log.error("Erro ao buscar NFC-e para o pagamento {}", pagamentoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao consultar NFC-e: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para reprocessar DANFCE (em caso de erro)
     * POST /api/danfce/{id}/reprocessar
     */
    @PostMapping("/{id}/reprocessar")
    public ResponseEntity<?> reprocessarDanfce(@PathVariable Long id) {
        try {
            log.info("Solicitação de reprocessamento DANFCE - ID: {}", id);

            // Buscar NFC-e
            Optional<NfeModel> nfeOpt = nfeRepository.findById(id);
            if (!nfeOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(criarRespostaErro("NFC-e não encontrada com ID: " + id));
            }

            NfeModel nfe = nfeOpt.get();

            // Validar status
            if (!"AUTORIZADA".equals(nfe.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(criarRespostaErro("NFC-e deve estar autorizada para reprocessamento"));
            }

            // Reprocessar
            DanfceModel danfce = nfceParserService.parseNfceXml(nfe);

            // Dados de resposta
            Map<String, Object> dados = new HashMap<>();
            dados.put("id", id);
            dados.put("chave", nfe.getChaveAcesso());
            dados.put("numero", danfce.getNumero());
            dados.put("serie", danfce.getSerie());
            dados.put("reprocessadoEm", java.time.LocalDateTime.now().toString());

            log.info("DANFCE reprocessado com sucesso - ID: {}", id);

            return ResponseEntity.ok(criarRespostaSucesso("DANFCE reprocessado com sucesso", dados));

        } catch (Exception e) {
            log.error("Erro ao reprocessar DANFCE - ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao reprocessar DANFCE: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para download em lote de vários DANFCE
     * POST /api/danfce/download-lote
     */
    @PostMapping("/download-lote")
    public void downloadLoteDanfce(@RequestBody List<Long> ids, HttpServletResponse response) {
        try {
            log.info("Solicitação de download em lote DANFCE - {} IDs", ids.size());

            if (ids.isEmpty() || ids.size() > 50) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.getWriter().write("{\"success\":false,\"message\":\"Limite de 1 a 50 documentos por vez\"}");
                return;
            }

            // Configurar response para ZIP
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    String.format("attachment; filename=\"danfce_lote_%d.zip\"", System.currentTimeMillis()));

            // Criar ZIP
            try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(response.getOutputStream())) {

                for (Long id : ids) {
                    try {
                        Optional<NfeModel> nfeOpt = nfeRepository.findById(id);
                        if (!nfeOpt.isPresent()) continue;

                        NfeModel nfe = nfeOpt.get();
                        DanfceModel danfce = nfceParserService.parseNfceXml(nfe);
                        byte[] pdfBytes = danfcePdfGenerator.generateDanfcePdf(danfce);

                        // Adicionar ao ZIP
                        String dataEmissaoStr = nfe.getDataEmissao() != null ? 
                            nfe.getDataEmissao().format(DateTimeFormatter.ofPattern("ddMMyyyy")) : "00000000";
                            
                        String fileName = String.format("danfce_%s_%s.pdf",
                                nfe.getNumero() != null ? nfe.getNumero() : "S_N",
                                dataEmissaoStr);

                        zipOut.putNextEntry(new java.util.zip.ZipEntry(fileName));
                        zipOut.write(pdfBytes);
                        zipOut.closeEntry();

                        log.debug("DANFCE adicionado ao ZIP - ID: {}", id);

                    } catch (Exception e) {
                        log.warn("Erro ao processar DANFCE ID: {} - {}", id, e.getMessage());
                        // Continua com os próximos
                    }
                }
            }

            log.info("Download em lote DANFCE concluído - {} documentos processados", ids.size());

        } catch (Exception e) {
            log.error("Erro no download em lote DANFCE", e);
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.getWriter().write("{\"success\":false,\"message\":\"Erro no download em lote\"}");
            } catch (IOException ioException) {
                log.error("Erro ao escrever resposta de erro", ioException);
            }
        }
    }

    /**
     * Endpoint para exportar dados de NFC-e em CSV
     * GET /api/danfce/export-csv
     */
    @GetMapping("/export-csv")
    public void exportarCsvNfce(@RequestParam(value = "dataInicio", required = false) String dataInicio,
                                @RequestParam(value = "dataFim", required = false) String dataFim,
                                @RequestParam(value = "status", required = false) String status,
                                HttpServletResponse response) {
        try {
            log.info("Solicitação de exportação CSV DANFCE - Período: {} a {}, Status: {}",
                    dataInicio, dataFim, status);

            // Configurar response
            response.setContentType("text/csv");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    String.format("attachment; filename=\"nfce_export_%d.csv\"", System.currentTimeMillis()));

            // Buscar dados com filtros apropriados
            List<NfeModel> nfces;

            // Por simplicidade, buscar todas as NFC-e (pode ser otimizado com filtros de data/status)
            if (status != null && !status.trim().isEmpty()) {
                nfces = nfeRepository.findByModeloAndStatusOrderByDataEmissaoDesc(65, status.toUpperCase());
            } else {
                nfces = nfeRepository.findByModeloOrderByDataEmissaoDesc(65);
            }

            // Escrever CSV
            try (java.io.PrintWriter writer = response.getWriter()) {
                // Cabeçalho
                writer.println("ID,Numero,Serie,ChaveAcesso,DataEmissao,ValorTotal,Status,Protocolo");

                // Dados
                for (NfeModel nfe : nfces) {
                    writer.printf("%d,%s,%s,%s,%s,%.2f,%s,%s%n",
                            nfe.getId(),
                            nfe.getNumero(),
                            nfe.getSerie(),
                            nfe.getChaveAcesso(),
                            nfe.getDataEmissao() != null ? nfe.getDataEmissao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) : "",
                            nfe.getValorTotal(),
                            nfe.getStatus(),
                            nfe.getProtocolo() != null ? nfe.getProtocolo() : ""
                    );
                }
            }

            log.info("Exportação CSV DANFCE concluída - {} registros", nfces.size());

        } catch (Exception e) {
            log.error("Erro na exportação CSV DANFCE", e);
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.getWriter().write("Erro na exportação CSV");
            } catch (IOException ioException) {
                log.error("Erro ao escrever resposta de erro CSV", ioException);
            }
        }
    }

    /**
     * Endpoint para obter estatísticas de NFC-e
     * GET /api/danfce/estatisticas
     */
    @GetMapping("/estatisticas")
    public ResponseEntity<?> obterEstatisticas() {
        try {
            log.info("Solicitação de estatísticas DANFCE");

            Map<String, Object> stats = new HashMap<>();

            // Contadores por status
            long totalNfce = nfeRepository.countByModelo(65);
            long autorizadas = nfeRepository.countByModeloAndStatus(65, "AUTORIZADA");
            long rejeitadas = nfeRepository.countByModeloAndStatus(65, "REJEITADA");
            long processando = nfeRepository.countByModeloAndStatus(65, "PROCESSANDO");

            stats.put("total", totalNfce);
            stats.put("autorizadas", autorizadas);
            stats.put("rejeitadas", rejeitadas);
            stats.put("processando", processando);

            // Percentuais
            if (totalNfce > 0) {
                stats.put("percentualAutorizadas", Math.round((autorizadas * 100.0) / totalNfce));
                stats.put("percentualRejeitadas", Math.round((rejeitadas * 100.0) / totalNfce));
            }

            // Últimas NFC-e
            List<NfeModel> ultimas = nfeRepository.findTop5ByModeloOrderByDataEmissaoDesc(65);
            stats.put("ultimasNfce", ultimas.stream().map(nfe -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", nfe.getId());
                item.put("numero", nfe.getNumero());
                item.put("dataEmissao", nfe.getDataEmissao());
                item.put("status", nfe.getStatus());
                item.put("valorTotal", nfe.getValorTotal());
                return item;
            }).collect(Collectors.toList()));

            return ResponseEntity.ok(criarRespostaSucesso("Estatísticas DANFCE", stats));

        } catch (Exception e) {
            log.error("Erro ao obter estatísticas DANFCE", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(criarRespostaErro("Erro ao obter estatísticas: " + e.getMessage()));
        }
    }

    // ========================================
    // MÉTODOS AUXILIARES
    // ========================================

    /**
     * Converte string para modo de impressão
     */
    private DanfcePdfGeneratorService.ModoImpressao obterModoImpressao(String modo) {
        try {
            return DanfcePdfGeneratorService.ModoImpressao.valueOf(modo.toUpperCase());
        } catch (Exception e) {
            log.warn("Modo de impressão inválido: {}. Usando padrão TERMICA_80MM", modo);
            return DanfcePdfGeneratorService.ModoImpressao.TERMICA_80MM;
        }
    }

    /**
     * Cria resposta padronizada de sucesso
     */
    private Map<String, Object> criarRespostaSucesso(String mensagem, Object dados) {
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("success", true);
        resposta.put("message", mensagem);
        resposta.put("data", dados);
        resposta.put("timestamp", System.currentTimeMillis());
        return resposta;
    }

    /**
     * Cria resposta padronizada de erro
     */
    private Map<String, Object> criarRespostaErro(String mensagem) {
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("success", false);
        resposta.put("message", mensagem);
        resposta.put("timestamp", System.currentTimeMillis());
        return resposta;
    }
}
