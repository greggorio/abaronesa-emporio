package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.ProdutoDTO;
import com.baronesa.emporio.dto.ProdutoMidiaDTO;
import com.baronesa.emporio.dto.ProdutoOptionDTO;
import com.baronesa.emporio.dto.ProdutoRequest;
import com.baronesa.emporio.dto.ProdutoSignageDTO;
import com.baronesa.emporio.dto.AiImageGenerationRequestDTO;
import com.baronesa.emporio.dto.AiImageGenerationResponseDTO;
import com.baronesa.emporio.dto.ProdutoSignageRequest;
import com.baronesa.emporio.dto.ProdutoSignagePreviewDTO;
import com.baronesa.emporio.dto.ProdutoSignageRenderRequest;
import com.baronesa.emporio.dto.SignageRenderResponseDTO;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.ProdutoService;
import com.baronesa.emporio.service.ProductSignageAiService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.ProdutoListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@Slf4j
public class ProdutoController extends BaseListController<ProdutoListService>
        implements FormConfigurableController {

    private final ProdutoListService produtoListService;
    private final ProdutoService produtoService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;
    private final ProductSignageAiService productSignageAiService;

    @PostMapping
    public ResponseEntity<ProdutoDTO> criar(@RequestBody ProdutoRequest request) {
        ProdutoDTO produto = produtoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(produto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoDTO> atualizar(@PathVariable Long id, @RequestBody ProdutoRequest request) {
        ProdutoDTO produto = produtoService.atualizar(id, request);
        return ResponseEntity.ok(produto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoDTO> buscarPorId(@PathVariable Long id) {
        ProdutoDTO produto = produtoService.buscarPorId(id);
        return ResponseEntity.ok(produto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        produtoService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/signage/preview")
    public ResponseEntity<ProdutoSignagePreviewDTO> visualizarSignage(@PathVariable Long id) {
        ProdutoSignagePreviewDTO preview = produtoService.carregarSignagePreview(id);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/{id}/signage/generate-ai-image")
    public ResponseEntity<AiImageGenerationResponseDTO> gerarImagemAi(@PathVariable Long id,
                                                                      @RequestParam(name = "force", defaultValue = "false") boolean force,
                                                                      @RequestBody(required = false) AiImageGenerationRequestDTO request) {
        boolean finalForce = request != null && request.getForce() != null ? request.getForce() : force;
        AiImageGenerationResponseDTO response = productSignageAiService.generateAiImage(id, finalForce);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/signage/render")
    public ResponseEntity<SignageRenderResponseDTO> renderizarVideoSignage(@PathVariable Long id,
                                                                           @RequestBody ProdutoSignageRenderRequest request) {
        SignageRenderResponseDTO response = produtoService.renderSignageVideo(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/signage")
    public ResponseEntity<ProdutoSignageDTO> atualizarSignage(@PathVariable Long id,
                                                              @RequestBody ProdutoSignageRequest request) {
        ProdutoSignageDTO signage = produtoService.atualizarSignage(id, request);
        return ResponseEntity.ok(signage);
    }

    @GetMapping("/options")
    public ResponseEntity<List<ProdutoOptionDTO>> listarOptions() {
        List<ProdutoOptionDTO> options = produtoService.listarOptions();
        return ResponseEntity.ok(options);
    }

    @GetMapping("/pendencias")
    public ResponseEntity<Map<String, Object>> listarPendencias(
            @RequestParam(name = "tipo", defaultValue = "sem-preco") String tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(name = "apenasAtivos", defaultValue = "true") boolean apenasAtivos,
            @RequestParam(name = "ordenacao", required = false) String ordenacao,
            @RequestParam(name = "direcao", required = false) String direcao
    ) {
        Map<String, Object> body = produtoListService.listarPendencias(tipo, pagina, tamanho, apenasAtivos, ordenacao, direcao);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/ativos")
    public ResponseEntity<List<ProdutoDTO>> listarAtivos() {
        List<ProdutoDTO> produtos = produtoService.listarAtivos();
        return ResponseEntity.ok(produtos);
    }

    @GetMapping("/cardapio")
    public ResponseEntity<List<ProdutoDTO>> listarCardapio() {
        // Este método pode ser expandido para incluir filtros específicos do cardápio
        List<ProdutoDTO> produtos = produtoService.listarAtivos().stream()
                .filter(ProdutoDTO::getExibirNoCardapio)
                .toList();
        return ResponseEntity.ok(produtos);
    }

    @Override
    protected ProdutoListService getService() {
        return produtoListService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "produtos";
    }

    @Override
    public BaseListService<?> getListService() {
        return produtoListService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    // Endpoints de mídia
    @PostMapping("/{id}/upload-imagem")
    public ResponseEntity<Map<String, String>> uploadImagemPrincipal(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String url = produtoService.uploadImagemPrincipal(id, file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/{id}/galeria")
    public ResponseEntity<ProdutoMidiaDTO> uploadImagemGaleria(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        ProdutoMidiaDTO midia = produtoService.uploadImagemGaleria(id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(midia);
    }

    @DeleteMapping("/{produtoId}/galeria/{midiaId}")
    public ResponseEntity<Void> deletarImagemGaleria(
            @PathVariable Long produtoId,
            @PathVariable Long midiaId) {
        produtoService.deletarImagemGaleria(produtoId, midiaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/video")
    public ResponseEntity<ProdutoMidiaDTO> uploadVideo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        ProdutoMidiaDTO midia = produtoService.uploadVideo(id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(midia);
    }
}
