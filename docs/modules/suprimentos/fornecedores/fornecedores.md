# Fornecedores — Especificação

## Entidade

### Fornecedor (`Fornecedor.java`, 101 linhas)

Tabela `fornecedor`. Implementa `LookupSearchable`.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK, `@GeneratedValue(IDENTITY)` |
| `razaoSocial` | `String` | Obrigatório |
| `nomeFantasia` | `String` | |
| `cnpj` | `String` | Único |
| `telefone` | `String` | |
| `email` | `String` | |
| `contato` | `String` | Nome da pessoa de contato |
| `endereco` | `String` | |
| `cidade` | `String` | |
| `estado` | `String` | |
| `cep` | `String` | |
| `ativo` | `Boolean` | Obrigatório, default `true` |

**Métodos**:
- `getNomeExibicao()` → `razaoSocial`
- `getLookupLabel()` → usado pelo componente Lookup genérico
- `getLookupData()` → metadados adicionais para lookup

## DTOs

| DTO | Tipo | Campos |
|-----|------|--------|
| `FornecedorDTO` | record | `id, razaoSocial, nomeFantasia, cnpj, telefone, email, contato, endereco, cidade, estado, cep, ativo, nomeExibicao` |
| `FornecedorRequest` | record | `razaoSocial` (`@NotBlank`), `nomeFantasia, cnpj, telefone, email, contato, endereco, cidade, estado, cep, ativo` |
| `FornecedorOptionDTO` | record | `value` (id), `label` (nomeExibicao), `cnpj` |
| `FornecedorNfeDTO` | record | `cnpj, razaoSocial, nomeFantasia, fornecedorId, cadastrado` (usado na importação de NF-e) |

## Repositório

`FornecedorRepository` — estende `JpaRepository<Fornecedor, Long>` e `JpaSpecificationExecutor<Fornecedor>`.

| Método | Descrição |
|--------|-----------|
| `findByCnpj(String)` | Busca exata por CNPJ |
| `findByAtivoTrue()` | Lista fornecedores ativos |
| `findAllAtivosOrdenados()` | JPQL: todos ativos ordenados |
| `existsByCnpjAndIdNot(String, Long)` | Verifica duplicidade de CNPJ ignorando próprio ID |
| `Page<Fornecedor> findByAtivoTrue(Pageable)` | Ativos paginados |
| `buscarPorCodigoRazaoOuCnpj(String search)` | JPQL LIKE em id, razaoSocial, cnpj |
| `searchForLookup(String search)` | JPQL LIKE em cnpj, razaoSocial, nomeFantasia, cidade, email, contato |

## Serviço

`FornecedorService` (205 linhas):

| Método | Descrição |
|--------|-----------|
| `listarTodos()` | Retorna `List<FornecedorDTO>` |
| `listarAtivos()` | Filtra `ativo = true`, retorna DTOs |
| `buscarPorId(Long)` | Retorna `FornecedorDTO` ou lança exceção |
| `criar(FornecedorRequest)` | Valida CNPJ único, salva, retorna DTO |
| `editar(Long, FornecedorRequest)` | Valida CNPJ único (exceto próprio), atualiza |
| `deletar(Long)` | Exclusão física (remove registro) |
| `buscarPorCnpj(String)` | `Optional<Fornecedor>` |
| `buscarParaLookup(String)` | `List<Map<String, Object>>` para lookup |
| `listarOptions()` | `List<FornecedorOptionDTO>` para dropdowns |

## Controller

`FornecedorController` (120 linhas) — `@RequestMapping("/api/fornecedores")`.

Também estende `BaseListController<FornecedorListService>` (endpoint `/api/fornecedores/list`) e implementa `FormConfigurableController` (endpoint `/api/fornecedores/form-config`).

`FornecedorLookupController` (37 linhas) — `@RequestMapping("/api/fornecedores/lookup")`, estende `BaseLookupController`.

### Endpoints

| Método | Path | Handler | Descrição |
|--------|------|---------|-----------|
| `GET` | `/api/fornecedores` | `list` (BaseListController) | Lista paginada |
| `GET` | `/api/fornecedores/form-config` | `getFormConfig` | Config dinâmica |
| `GET` | `/api/fornecedores/{id}` | `buscarPorId` | Por ID |
| `POST` | `/api/fornecedores` | `criar` | Criar (201) |
| `PUT` | `/api/fornecedores/{id}` | `editar` | Atualizar |
| `DELETE` | `/api/fornecedores/{id}` | `deletar` | Excluir (204) |
| `GET` | `/api/fornecedores/options` | `listarOptions` | Dropdown (id/name/cnpj) |
| `GET` | `/api/fornecedores/optionsfornecedor` | `listarOptions` | Dropdown (duplicata) |
| `GET` | `/api/fornecedores/ativos` | `listarAtivos` | Apenas ativos |
| `GET` | `/api/fornecedores/search` | `buscarParaLookup` | Busca textual |
| `GET` | `/api/fornecedores/lookup/search` | LookupController | Lookup com searchForLookup |

### ListService

`FornecedorListService` (57 linhas) — estende `BaseListService<Fornecedor>`. Mapeia: `id, razaoSocial, nomeFantasia, nomeExibicao, cnpj, telefone, email, contato, endereco, cidade, estado, cep, ativo`.

## Regras de negócio

1. **CNPJ único**: validado tanto na criação quanto na edição (ignorando o próprio ID)
2. **Exclusão lógica**: a flag `ativo` permite desativar sem perder o vínculo com pedidos/recebimentos passados — porém o `deletar()` no controller faz exclusão física
3. **Busca textual abrangente**: CNPJ, razão social, nome fantasia, cidade, email e contato são pesquisáveis simultaneamente via `searchForLookup`
4. **LookupSearchable**: interface que expõe `getLookupLabel()` e `getLookupData()` para o componente de lookup genérico do frontend
