package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.EventoDashboardDTO;
import com.baronesa.emporio.dto.PagamentoEventoDTO;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventoEspressoService {

    private static final String ESPRESSO_API_BASE_URL_KEY = "espresso-api.app.base-url";

    private final ConfigManager configManager;
    private final PagamentoRepository pagamentoRepository;
    private final RestClient restClient = RestClient.create();

    @Value("${espresso-api.app.base-url:${ESPRESSO_API_BASE_URL:http://localhost:8085}}")
    private String espressoApiBaseUrlDefault;

    /**
     * Busca o valor do couvert artístico para o primeiro evento ativo no sistema espresso
     * @return BigDecimal com o valor do couvert ou null se não encontrado ou gratuito
     */
    public BigDecimal buscarValorCouvert(Long eventoId) {
        // Para manter compatibilidade, apenas retornamos null (não usamos mais eventoId)
        // O valor do couvert é obtido através do primeiro evento ativo
        return null;
    }

    /**
     * Busca o evento ativo no sistema espresso (primeiro da lista de próximos)
     * @return ID do evento ativo ou null se não encontrado
     */
    public Long buscarEventoAtivo() {
        try {
            // Ler base URL da config existente: espresso-api.app.base-url
            String baseUrl = obterBaseUrlEspressoApi();
            if (baseUrl == null) {
                return null;
            }
            String url = String.format("%s/api/eventos/proximos", baseUrl);

            // Faz a requisição para o sistema espresso
            List<Map<String, Object>> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(List.class);

            if (response != null && !response.isEmpty()) {
                // Selecionar o primeiro evento retornado como "ativo"
                Map<String, Object> primeiroEvento = response.get(0);

                // Exemplo de payload parseado:
                // {
                //   "id": 123,
                //   "preco": 25.00,
                //   "gratuito": false
                // }

                Object eventoId = primeiroEvento.get("id");
                if (eventoId != null) {
                    if (eventoId instanceof Number) {
                        return ((Number) eventoId).longValue();
                    } else if (eventoId instanceof String) {
                        return Long.valueOf((String) eventoId);
                    }
                }
            }

            log.warn("Evento ativo não encontrado no espresso");
            return null;
        } catch (Exception e) {
            log.error("Erro ao buscar evento ativo no espresso", e);
            return null;
        }
    }

    /**
     * Lista eventos próximos no sistema espresso.
     * @return Lista de mapas com dados do evento
     */
    public List<Map<String, Object>> listarEventosProximos() {
        try {
            String baseUrl = obterBaseUrlEspressoApi();
            if (baseUrl == null) {
                return new ArrayList<>();
            }
            String url = String.format("%s/api/eventos/proximos", baseUrl);
            List<Map<String, Object>> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(List.class);
            return response != null ? response : new ArrayList<>();
        } catch (Exception e) {
            log.error("Erro ao listar eventos próximos no espresso", e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca o valor do couvert artístico do primeiro evento ativo
     * @return BigDecimal com o valor do couvert ou null se gratuito ou não encontrado
     */
    public BigDecimal buscarValorCouvertAtivo() {
        try {
            // Ler base URL da config existente: espresso-api.app.base-url
            String baseUrl = obterBaseUrlEspressoApi();
            if (baseUrl == null) {
                return null;
            }
            String url = String.format("%s/api/eventos/proximos", baseUrl);

            // Faz a requisição para o sistema espresso
            List<Map<String, Object>> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(List.class);

            if (response != null && !response.isEmpty()) {
                // Percorrer eventos para encontrar o primeiro que está ativo no momento
                for (Map<String, Object> evento : response) {
                    // Exemplo de payload parseado:
                    // {
                    //   "id": 123,
                    //   "preco": 25.00,  // mapear para valorCouvert
                    //   "gratuito": false,  // se gratuito == true -> retornar valorCouvert null
                    //   "dataEvento": "2025-12-24T20:00:00",  // data/hora de início do evento
                    //   "dataHoraFim": "2025-12-24T23:00:00"  // data/hora de término do evento
                    // }

                    // Verificar se o evento é gratuito
                    Object gratuito = evento.get("gratuito");
                    if (gratuito instanceof Boolean && (Boolean) gratuito) {
                        log.info("Evento é gratuito, verificando próximo evento");
                        continue; // Pular eventos gratuitos
                    }

                    // Verificar se o evento está atualmente ativo com base no tempo
                    boolean eventoAtivo = isEventoAtivo(evento);
                    if (!eventoAtivo) {
                        log.debug("Evento não está ativo no momento, verificando próximo evento");
                        continue; // Pular eventos que não estão ativos
                    }

                    // Obter o valor do couvert
                    Object preco = evento.get("preco");
                    if (preco != null) {
                        if (preco instanceof Number) {
                            log.info("Evento ativo encontrado, retornando valor do couvert: {}", preco);
                            return new BigDecimal(preco.toString());
                        } else if (preco instanceof String) {
                            log.info("Evento ativo encontrado, retornando valor do couvert: {}", preco);
                            return new BigDecimal((String) preco);
                        }
                    }
                }
            }

            log.warn("Nenhum evento ativo com couvert encontrado no espresso");
            return null;
        } catch (Exception e) {
            log.error("Erro ao buscar valor do couvert no espresso", e);
            return null;
        }
    }

    /**
     * Verifica se um evento está ativo no momento atual com base em data e hora
     * @param evento Map contendo os dados do evento
     * @return true se o evento estiver ativo, false caso contrário
     */
    private boolean isEventoAtivo(Map<String, Object> evento) {
        try {
            String dataInicioStr = (String) evento.get("dataEvento");
            String dataFimStr = (String) evento.get("dataHoraFim");

            if (dataInicioStr != null && dataFimStr != null) {
                LocalDateTime dataInicioEvento = LocalDateTime.parse(dataInicioStr);
                LocalDateTime dataFimEvento = LocalDateTime.parse(dataFimStr);
                LocalDateTime agora = LocalDateTime.now();

                // Verificar se o horário atual está dentro do intervalo do evento
                boolean estaNoIntervalo = !agora.isBefore(dataInicioEvento) && !agora.isAfter(dataFimEvento);
                log.debug("Evento: {} às {}, agora: {}, ativo: {}",
                         dataInicioEvento, dataFimEvento, agora, estaNoIntervalo);

                return estaNoIntervalo;
            }
        } catch (Exception e) {
            log.warn("Erro ao verificar se evento está ativo (possível ausência de data/hora no evento): {}", e.getMessage());
            // Em caso de erro na verificação de tempo, considerar o evento como ativo para manter compatibilidade
            return true;
        }

        // Se não houver informações de tempo, considerar o evento como ativo
        return true;
    }

    /**
     * Recupera dados para o dashboard de eventos
     * @param periodo período para filtrar os dados (hoje, 7d, 30d)
     * @return DTO com dados do dashboard de eventos
     */
    public EventoDashboardDTO buscarDadosDashboard(String periodo) {
        // Obtém dados reais de faturamento a partir do banco de dados local
        return buscarDadosFaturamentoPorEvento(periodo);
    }

    /**
     * Converte métricas de faturamento recebidas do sistema externo para DTO
     * @param metricasFaturamento Métricas recebidas do sistema externo
     * @return DTO com os dados formatados para o dashboard
     */
    private EventoDashboardDTO converterMetricasFaturamentoParaDTO(Map<String, Object> metricasFaturamento) {
        // Extrair valores das métricas de faturamento
        Object totalCouverAtual = metricasFaturamento.get("totalCouverAtual");
        Object totalFaturamento30d = metricasFaturamento.get("totalFaturamento30d");
        Object mediaFaturamento = metricasFaturamento.get("mediaFaturamento");
        Object eventosListaObj = metricasFaturamento.get("eventosLista");

        BigDecimal totalCouver = totalCouverAtual != null ? new BigDecimal(totalCouverAtual.toString()) : BigDecimal.ZERO;
        BigDecimal totalFaturamento = totalFaturamento30d != null ? new BigDecimal(totalFaturamento30d.toString()) : BigDecimal.ZERO;
        BigDecimal mediaFat = mediaFaturamento != null ? new BigDecimal(mediaFaturamento.toString()) : BigDecimal.ZERO;

        List<EventoDashboardDTO.EventoResumoDTO> eventosResumo = new ArrayList<>();
        if (eventosListaObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eventosLista = (List<Map<String, Object>>) eventosListaObj;

            for (Map<String, Object> eventoMap : eventosLista) {
                String nome = (String) eventoMap.get("nome");
                Object totalObj = eventoMap.get("total");
                Object progressWidthObj = eventoMap.get("progressWidth");

                BigDecimal total = totalObj != null ? new BigDecimal(totalObj.toString()) : BigDecimal.ZERO;
                Integer progressWidth = progressWidthObj != null ? Integer.parseInt(progressWidthObj.toString()) : 0;

                eventosResumo.add(new EventoDashboardDTO.EventoResumoDTO(nome, total, progressWidth));
            }
        }

        return new EventoDashboardDTO(totalCouver, totalFaturamento, mediaFat, eventosResumo);
    }

    /**
     * Busca dados reais de faturamento por evento a partir do banco de dados local
     * @param periodo Período para filtrar os dados (hoje, 7d, 30d)
     * @return DTO com dados reais de faturamento por evento
     */
    public EventoDashboardDTO buscarDadosFaturamentoPorEvento(String periodo) {
        LocalDateTime dataInicio = calcularDataInicio(periodo);
        LocalDateTime dataFim = LocalDateTime.now();

        List<Pagamento> pagamentos = pagamentoRepository.findByPagoEmBetweenAndValorCouvertIsNotNull(dataInicio, dataFim);

        List<Map<String, Object>> eventos = buscarEventosDoSistemaExterno(periodo);

        return calcularMetricasFaturamentoReal(pagamentos, eventos, periodo);
    }

    /**
     * Calcula data de início com base no período
     * @param periodo Período (hoje, 7d, 30d)
     * @return Data de início calculada
     */
    private LocalDateTime calcularDataInicio(String periodo) {
        return switch (periodo) {
            case "hoje" -> LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> LocalDateTime.now().minusDays(30); // padrão para 30 dias
        };
    }

    private String obterBaseUrlEspressoApi() {
        String configurada = configManager.getConfig(ESPRESSO_API_BASE_URL_KEY, null);
        String baseUrl = isBlank(configurada) ? null : configurada.trim();

        // Fallback para chave criada manualmente ou legado
        if (isBlank(baseUrl)) {
            String eventosBaseUrl = configManager.getConfig("eventos.app.base-url", null);
            if (!isBlank(eventosBaseUrl)) {
                log.warn("Config '{}' vazia; usando valor de 'eventos.app.base-url' e sincronizando.", ESPRESSO_API_BASE_URL_KEY);
                baseUrl = eventosBaseUrl.trim();
            }
        }

        // Fallback para o valor definido em application.properties/env
        if (isBlank(baseUrl) && !isBlank(espressoApiBaseUrlDefault)) {
            log.warn("Config '{}' vazia; aplicando valor padrão das propriedades e persistindo.", ESPRESSO_API_BASE_URL_KEY);
            baseUrl = espressoApiBaseUrlDefault.trim();
        }

        if (isBlank(baseUrl)) {
            log.error("URL do sistema espresso não configurada. Verifique a configuração '{}'", ESPRESSO_API_BASE_URL_KEY);
            return null;
        }

        // Persistir o valor utilizado para evitar nova ausência
        if (isBlank(configurada) || !baseUrl.equals(configurada)) {
            configManager.setConfig(ESPRESSO_API_BASE_URL_KEY, baseUrl);
        }
        return baseUrl;
    }

    /**
     * Calcula métricas reais de faturamento com base nos pagamentos e eventos
     * @param pagamentos Lista de pagamentos
     * @param eventos Lista de eventos do sistema externo
     * @param periodo Período analisado
     * @return DTO com métricas calculadas
     */
    private EventoDashboardDTO calcularMetricasFaturamentoReal(List<Pagamento> pagamentos, List<Map<String, Object>> eventos, String periodo) {
        // Mapear pagamentos por evento
        Map<Long, List<Pagamento>> pagamentosPorEvento = mapearPagamentosPorEvento(pagamentos, eventos);

        // Calcular métricas
        BigDecimal totalCouverAtual = calcularTotalCouverAtual(pagamentosPorEvento, eventos);
        BigDecimal totalFaturamento30d = calcularTotalFaturamento(pagamentos);
        BigDecimal mediaFaturamento = calcularMediaFaturamento(pagamentosPorEvento);

        // Criar lista de eventos para o dashboard
        List<EventoDashboardDTO.EventoResumoDTO> eventosResumo = criarEventosResumo(pagamentosPorEvento, eventos);

        return new EventoDashboardDTO(totalCouverAtual, totalFaturamento30d, mediaFaturamento, eventosResumo);
    }

    /**
     * Mapeia pagamentos por evento com base na data/hora do pagamento e do evento
     * @param pagamentos Lista de pagamentos
     * @param eventos Lista de eventos
     * @return Mapa de pagamentos agrupados por ID de evento
     */
    private Map<Long, List<Pagamento>> mapearPagamentosPorEvento(List<Pagamento> pagamentos, List<Map<String, Object>> eventos) {
        Map<Long, List<Pagamento>> mapeamento = new HashMap<>();

        for (Pagamento pagamento : pagamentos) {
            if (pagamento.getPagoEm() != null && pagamento.getValorCouvert() != null) {
                // Tentar associar o pagamento a um evento com base na data/hora
                Long eventoId = associarPagamentoAEvento(pagamento, eventos);
                if (eventoId != null) {
                    mapeamento.computeIfAbsent(eventoId, k -> new ArrayList<>()).add(pagamento);
                }
            }
        }

        return mapeamento;
    }

    /**
     * Associa um pagamento a um evento com base na proximidade temporal
     * @param pagamento Pagamento a ser associado
     * @param eventos Lista de eventos
     * @return ID do evento associado ou null se não encontrado
     */
    private Long associarPagamentoAEvento(Pagamento pagamento, List<Map<String, Object>> eventos) {
        if (pagamento.getPagoEm() == null) return null;

        for (Map<String, Object> evento : eventos) {
            try {
                String dataEventoStr = (String) evento.get("dataEvento");
                String dataFimStr = (String) evento.get("dataHoraFim");

                if (dataEventoStr != null && dataFimStr != null) {
                    LocalDateTime dataInicioEvento = LocalDateTime.parse(dataEventoStr);
                    LocalDateTime dataFimEvento = LocalDateTime.parse(dataFimStr);

                    // Verificar se o pagamento ocorreu durante o evento
                    if (pagamento.getPagoEm().isAfter(dataInicioEvento) &&
                        pagamento.getPagoEm().isBefore(dataFimEvento)) {

                        Object eventoIdObj = evento.get("id");
                        if (eventoIdObj instanceof Number) {
                            return ((Number) eventoIdObj).longValue();
                        } else if (eventoIdObj instanceof String) {
                            return Long.valueOf((String) eventoIdObj);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao associar pagamento ao evento: {}", evento.get("id"), e);
            }
        }

        return null; // Não encontrou evento correspondente
    }

    /**
     * Calcula o total de couvert atual (possivelmente do evento ativo)
     * @param pagamentos Lista de pagamentos
     * @return Total de couvert
     */
    private BigDecimal calcularTotalCouverAtual(Map<Long, List<Pagamento>> pagamentosPorEvento,
                                                List<Map<String, Object>> eventos) {
        Long eventoAtualId = identificarEventoAtual(eventos);
        if (eventoAtualId != null) {
            List<Pagamento> pagamentosEvento = pagamentosPorEvento.get(eventoAtualId);
            if (pagamentosEvento != null && !pagamentosEvento.isEmpty()) {
                return pagamentosEvento.stream()
                        .filter(p -> p.getValorCouvert() != null)
                        .map(Pagamento::getValorCouvert)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }

        return pagamentosPorEvento.values().stream()
                .flatMap(List::stream)
                .filter(p -> p.getValorCouvert() != null)
                .map(Pagamento::getValorCouvert)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Long identificarEventoAtual(List<Map<String, Object>> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return null;
        }

        LocalDateTime agora = LocalDateTime.now();
        for (Map<String, Object> evento : eventos) {
            try {
                String inicioStr = (String) evento.get("dataEvento");
                String fimStr = (String) evento.get("dataHoraFim");
                if (inicioStr == null || fimStr == null) {
                    continue;
                }
                LocalDateTime inicio = LocalDateTime.parse(inicioStr);
                LocalDateTime fim = LocalDateTime.parse(fimStr);
                if (!agora.isBefore(inicio) && !agora.isAfter(fim)) {
                    Object id = evento.get("id");
                    if (id instanceof Number) {
                        return ((Number) id).longValue();
                    } else if (id instanceof String) {
                        return Long.valueOf((String) id);
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao identificar evento atual (possível formato inesperado): {}", evento.get("id"), e);
            }
        }

        return null;
    }

    /**
     * Calcula o total de faturamento no período
     * @param pagamentos Lista de pagamentos
     * @return Total de faturamento
     */
    private BigDecimal calcularTotalFaturamento(List<Pagamento> pagamentos) {
        return pagamentos.stream()
                .filter(p -> p.getValorCouvert() != null)
                .map(Pagamento::getValorCouvert)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula a média de faturamento por evento
     * @param pagamentosPorEvento Mapa de pagamentos por evento
     * @return Média de faturamento
     */
    private BigDecimal calcularMediaFaturamento(Map<Long, List<Pagamento>> pagamentosPorEvento) {
        if (pagamentosPorEvento.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalFaturamento = pagamentosPorEvento.values().stream()
                .flatMap(List::stream)
                .filter(p -> p.getValorCouvert() != null)
                .map(Pagamento::getValorCouvert)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalFaturamento.divide(
            new BigDecimal(pagamentosPorEvento.size()),
            RoundingMode.HALF_UP
        );
    }


    /**
     * Cria lista de eventos resumo para o dashboard
     * @param pagamentosPorEvento Mapa de pagamentos por evento
     * @param eventos Lista de eventos do sistema externo
     * @return Lista de eventos resumo
     */
    private List<EventoDashboardDTO.EventoResumoDTO> criarEventosResumo(
            Map<Long, List<Pagamento>> pagamentosPorEvento,
            List<Map<String, Object>> eventos) {

        List<EventoDashboardDTO.EventoResumoDTO> eventosResumo = new ArrayList<>();

        for (Map.Entry<Long, List<Pagamento>> entry : pagamentosPorEvento.entrySet()) {
            Long eventoId = entry.getKey();
            List<Pagamento> pagamentosDoEvento = entry.getValue();

            // Encontrar o título do evento
            String tituloEvento = eventos.stream()
                    .filter(e -> {
                        Object id = e.get("id");
                        if (id instanceof Number) {
                            return ((Number) id).longValue() == eventoId;
                        } else if (id instanceof String) {
                            return Long.valueOf((String) id).equals(eventoId);
                        }
                        return false;
                    })
                    .map(e -> (String) e.get("titulo"))
                    .findFirst()
                    .orElse("Evento Desconhecido");

            // Calcular total do evento
            BigDecimal totalEvento = pagamentosDoEvento.stream()
                    .filter(p -> p.getValorCouvert() != null)
                    .map(Pagamento::getValorCouvert)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calcular progresso (em relação ao maior valor)
            BigDecimal valorMaximo = pagamentosPorEvento.values().stream()
                    .flatMap(List::stream)
                    .filter(p -> p.getValorCouvert() != null)
                    .map(Pagamento::getValorCouvert)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ONE);

            int progresso = valorMaximo.compareTo(BigDecimal.ZERO) > 0 ?
                Math.min(100, totalEvento.multiply(new BigDecimal(100))
                    .divide(valorMaximo, RoundingMode.HALF_UP).intValue()) : 0;

            eventosResumo.add(new EventoDashboardDTO.EventoResumoDTO(tituloEvento, totalEvento, progresso));
        }

        // Ordenar por total (decrescente) e pegar os top 5
        return eventosResumo.stream()
                .sorted((a, b) -> b.getTotal().compareTo(a.getTotal()))
                .limit(5)
                .toList();
    }

    /**
     * Busca eventos do sistema externo espresso
     * @return Lista de eventos
     */
    private List<Map<String, Object>> buscarEventosDoSistemaExterno(String periodo) {
        String baseUrl = obterBaseUrlEspressoApi();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A URL do sistema Espresso não está configurada. Atualize a configuração '" + ESPRESSO_API_BASE_URL_KEY + "'.");
        }

        String periodoNormalizado = periodo == null || periodo.isBlank() ? "30d" : periodo;
        String url = String.format("%s/api/eventos/dashboard?periodo=%s", baseUrl, periodoNormalizado);

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eventos = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(List.class);

            if (eventos == null) {
                log.warn("Resposta vazia ao buscar eventos do sistema externo");
                return new ArrayList<>();
            }

            return eventos;
        } catch (RestClientException e) {
            log.error("Erro ao buscar eventos do sistema externo", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível recuperar os eventos reais do Espresso. Verifique o acesso e tente novamente.", e);
        }
    }

    /**
     * Busca métricas de faturamento para eventos no sistema externo espresso
     * @param periodo Período para filtrar as métricas
     * @return Mapa com métricas de faturamento
     */
    private Map<String, Object> buscarMetricasFaturamento(String periodo) {
        try {
            String baseUrl = obterBaseUrlEspressoApi();
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                return new HashMap<>();
            }

            // Endpoint ideal que deveria existir no sistema espresso
            String url = String.format("%s/api/eventos/faturamento?periodo=%s", baseUrl, periodo);

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                return response;
            }

            log.warn("Nenhuma métrica de faturamento encontrada para o período: {}", periodo);
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Erro ao buscar métricas de faturamento do sistema externo para período: {}", periodo, e);
            return new HashMap<>();
        }
    }

    /**
     * Filtra eventos com base no período especificado
     * @param eventos Lista de eventos
     * @param periodo Período para filtrar (hoje, 7d, 30d)
     * @return Lista de eventos filtrados
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filtrarEventosPorPeriodo(List<Map<String, Object>> eventos, String periodo) {
        if (eventos == null || eventos.isEmpty()) {
            return new ArrayList<>();
        }

        // Converter período para dias
        int dias = switch (periodo) {
            case "hoje" -> 1;
            case "7d" -> 7;
            case "30d" -> 30;
            default -> 30; // padrão para 30 dias
        };

        // Calcular data limite
        LocalDateTime dataLimite = LocalDateTime.now()
                .minusDays(dias)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        return eventos.stream()
                .filter(evento -> {
                    try {
                        String dataEventoStr = (String) evento.get("dataEvento");
                        if (dataEventoStr != null) {
                            LocalDateTime dataEvento = LocalDateTime.parse(dataEventoStr);
                            // Considerar eventos que já ocorreram (data passada)
                            return dataEvento.isAfter(dataLimite) && dataEvento.isBefore(LocalDateTime.now());
                        }
                    } catch (Exception e) {
                        log.warn("Erro ao parsear data do evento: {}", evento.get("id"), e);
                    }
                    return false;
                })
                .toList();
    }

    /**
     * Calcula métricas com base nos eventos filtrados
     * @param eventosFiltrados Eventos filtrados por período
     * @param periodo Período analisado
     * @return DTO com métricas calculadas
     */
    private EventoDashboardDTO calcularMetricasEventos(List<Map<String, Object>> eventosFiltrados, String periodo) {
        // Para esta implementação inicial, vamos retornar valores mockados baseados nos eventos reais
        // conforme vamos evoluindo, implementaremos as métricas reais

        // Contar eventos filtrados
        int totalEventos = eventosFiltrados.size();

        // Calcular valores baseados nos eventos filtrados
        BigDecimal totalFaturamento = BigDecimal.ZERO;
        for (Map<String, Object> evento : eventosFiltrados) {
            Object preco = evento.get("preco");
            if (preco != null) {
                BigDecimal precoEvento = new BigDecimal(preco.toString());
                totalFaturamento = totalFaturamento.add(precoEvento);
            }
        }

        BigDecimal mediaFaturamento = totalEventos > 0 ?
            totalFaturamento.divide(new BigDecimal(totalEventos), RoundingMode.HALF_UP) :
            BigDecimal.ZERO;

        // Criar lista de eventos para o dashboard (usando os eventos reais)
        List<EventoDashboardDTO.EventoResumoDTO> eventosResumo = new ArrayList<>();
        for (int i = 0; i < Math.min(eventosFiltrados.size(), 5); i++) {
            Map<String, Object> evento = eventosFiltrados.get(i);
            Object preco = evento.get("preco");
            BigDecimal precoEvento = preco != null ? new BigDecimal(preco.toString()) : BigDecimal.ZERO;

            // Calcular progresso baseado no preço em relação ao máximo
            BigDecimal precoMax = eventosFiltrados.stream()
                .map(e -> new BigDecimal(e.get("preco").toString()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);

            int progresso = precoMax.compareTo(BigDecimal.ZERO) > 0 ?
                (int) (precoEvento.divide(precoMax, RoundingMode.HALF_UP).doubleValue() * 100) : 0;

            eventosResumo.add(new EventoDashboardDTO.EventoResumoDTO(
                (String) evento.get("titulo"),
                precoEvento,
                Math.min(progresso, 100) // Limitar a 100%
            ));
        }

        // Para o total couvert atual, vamos considerar o couvert do primeiro evento ativo (caso exista)
        BigDecimal totalCouverAtual = buscarValorCouvertAtivo();

        return new EventoDashboardDTO(
            totalCouverAtual != null ? totalCouverAtual : BigDecimal.ZERO,
            totalFaturamento,
            mediaFaturamento,
            eventosResumo
        );
    }

    /**
     * Gera dados mockados para o dashboard de eventos
     * @param periodo período para filtrar os dados
     * @return DTO com dados mockados do dashboard de eventos
     */
    private EventoDashboardDTO gerarDadosMockados(String periodo) {
        switch (periodo) {
            case "hoje":
                return new EventoDashboardDTO(
                    new BigDecimal("450.00"), // totalCouverAtual
                    new BigDecimal("2450.75"), // totalFaturamento30d
                    new BigDecimal("1225.38"), // mediaFaturamento
                    List.of(
                        new EventoDashboardDTO.EventoResumoDTO("Evento Hoje 1", new BigDecimal("1500.00"), 100),
                        new EventoDashboardDTO.EventoResumoDTO("Evento Hoje 2", new BigDecimal("950.75"), 63)
                    )
                );
            case "7d":
                return new EventoDashboardDTO(
                    new BigDecimal("850.00"), // totalCouverAtual
                    new BigDecimal("8420.50"), // totalFaturamento30d
                    new BigDecimal("2806.83"), // mediaFaturamento
                    List.of(
                        new EventoDashboardDTO.EventoResumoDTO("Festa Aniversário", new BigDecimal("3500.00"), 100),
                        new EventoDashboardDTO.EventoResumoDTO("Evento Corporativo", new BigDecimal("2800.50"), 80),
                        new EventoDashboardDTO.EventoResumoDTO("Chá Revelação", new BigDecimal("2120.00"), 60)
                    )
                );
            case "30d":
            default:
                return new EventoDashboardDTO(
                    new BigDecimal("1250.00"), // totalCouverAtual
                    new BigDecimal("15420.50"), // totalFaturamento30d
                    new BigDecimal("3084.10"), // mediaFaturamento
                    List.of(
                        new EventoDashboardDTO.EventoResumoDTO("Festa Junina", new BigDecimal("4123.00"), 100),
                        new EventoDashboardDTO.EventoResumoDTO("Aniversário VIP", new BigDecimal("3800.50"), 84),
                        new EventoDashboardDTO.EventoResumoDTO("Casamento Chique", new BigDecimal("3200.00"), 70),
                        new EventoDashboardDTO.EventoResumoDTO("Confraternização", new BigDecimal("2500.75"), 55),
                        new EventoDashboardDTO.EventoResumoDTO("Evento Corporativo", new BigDecimal("1420.50"), 31)
                    )
                );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
