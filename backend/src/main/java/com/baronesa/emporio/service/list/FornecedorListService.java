package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.entity.Fornecedor;
import com.baronesa.emporio.repository.FornecedorRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FornecedorListService extends BaseListService<Fornecedor> {

    private final FornecedorRepository fornecedorRepository;

    @Override
    protected JpaSpecificationExecutor<Fornecedor> getRepository() {
        return fornecedorRepository;
    }

    @Override
    protected Class<Fornecedor> getEntityClass() {
        return Fornecedor.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        // Como usamos formulários dinâmicos, retornamos null
        return null;
    }

    @Override
    protected Map<String, Object> entityToRow(Fornecedor entity) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", entity.getId());
        row.put("razaoSocial", entity.getRazaoSocial());
        row.put("nomeFantasia", entity.getNomeFantasia());
        row.put("nomeExibicao", entity.getNomeExibicao());
        row.put("cnpj", entity.getCnpj());
        row.put("telefone", entity.getTelefone());
        row.put("email", entity.getEmail());
        row.put("contato", entity.getContato());
        row.put("endereco", entity.getEndereco());
        row.put("cidade", entity.getCidade());
        row.put("estado", entity.getEstado());
        row.put("cep", entity.getCep());
        row.put("ativo", entity.getAtivo());
        return row;
    }
}