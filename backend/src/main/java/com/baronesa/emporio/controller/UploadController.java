package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/produtos")
    public ResponseEntity<String> uploadImagemProduto(@RequestParam("file") MultipartFile file) {
        String url = uploadService.salvarImagemProduto(file);
        return ResponseEntity.ok(url);
    }

    // Nesta fase inicial, apenas upload de imagem de produtos
}
