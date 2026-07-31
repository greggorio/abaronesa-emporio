package com.baronesa.emporio.controller.lookup;

import com.baronesa.emporio.controller.base.BaseLookupController;
import com.baronesa.emporio.entity.Fornecedor;
import com.baronesa.emporio.repository.FornecedorRepository;
import com.baronesa.emporio.service.lookup.GenericLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de lookup para fornecedores
 * Como Fornecedor implementa diretamente LookupSearchable, é muito simples
 */
@RestController
@RequestMapping("/api/fornecedores/lookup")
@RequiredArgsConstructor
public class FornecedorLookupController extends BaseLookupController<Fornecedor, FornecedorRepository> {

    private final FornecedorRepository fornecedorRepository;
    private final GenericLookupService lookupService;

    @Override
    protected FornecedorRepository getRepository() {
        return fornecedorRepository;
    }

    @Override
    protected GenericLookupService getLookupService() {
        return lookupService;
    }

    @Override
    protected GenericLookupService.LookupSearchMethod<Fornecedor> getSearchMethod() {
        // Busca customizada usando o método @Query que busca em múltiplos campos
        return (searchTerm) -> fornecedorRepository.searchForLookup(searchTerm);
    }
}