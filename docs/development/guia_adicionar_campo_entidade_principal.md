# Guia para Adicionar Novos Campos à Entidade Principal

Este documento descreve o processo para adicionar novos campos diretamente à entidade principal do sistema (ex: `Usuario.java`).

## Visão Geral

Quando um novo campo deve ser adicionado à entidade principal, o processo é relativamente simples e envolve alterações em poucas camadas do sistema.

## Passos para adicionar um novo campo

### 1. Atualizar a entidade principal

Adicione o novo atributo na classe da entidade principal:

```java
// Em Usuario.java
@Column(name = "departamento", length = 100)
private String departamento;
```

Certifique-se de que os getters e setters estejam disponíveis (ou use `@Data` do Lombok).

### 2. Criar migration para atualizar o banco de dados

Crie um novo arquivo de migration para adicionar a coluna no banco de dados:

```sql
-- Arquivo: src/main/resources/db/migration/V4__add_departamento_to_usuarios.sql
ALTER TABLE usuarios ADD COLUMN departamento VARCHAR(100);
```

### 3. Atualizar os DTOs

Adicione o novo campo nos DTOs relevantes:

```java
// Em UsuarioAdminDTO.java
public record UsuarioAdminDTO(
    // ... outros campos
    String departamento
) {}

// Em UsuarioAdminRequest.java
public record UsuarioAdminRequest(
    // ... outros campos
    String departamento
) {}

// Em UsuarioAdminUpdateRequest.java
public record UsuarioAdminUpdateRequest(
    // ... outros campos
    String departamento
) {}
```

### 4. Atualizar o serviço

Modifique o serviço para manipular o novo campo:

```java
// No UsuarioAdminService.java
// No método criar():
usuario.setDepartamento(request.departamento());

// No método editar():
usuario.setDepartamento(request.departamento());

// No método entityToDTO():
departamento(usuario.getDepartamento())
```

## Considerações Importantes

- **Tipo de dados**: Escolha o tipo de dado apropriado (String, Integer, BigDecimal, etc.)
- **Validações**: Adicione validações se necessário (ex: `@NotBlank`, `@Size`, etc.)
- **Banco de dados**: Ajuste o tamanho e tipo da coluna conforme o tipo de dado
- **Formulários dinâmicos**: Se o campo deve aparecer na interface, ele já estará disponível automaticamente nos formulários (desde que esteja nos DTOs)

## Exemplo Completo

Vamos adicionar um campo "departamento" à entidade Usuario:

1. **Entidade Usuario.java**:
   ```java
   @Column(name = "departamento", length = 100)
   private String departamento;
   ```

2. **Migration V4__add_departamento_to_usuarios.sql**:
   ```sql
   ALTER TABLE usuarios ADD COLUMN departamento VARCHAR(100);
   ```

3. **DTOs** (adicionando `String departamento`):
   - UsuarioAdminDTO
   - UsuarioAdminRequest
   - UsuarioAdminUpdateRequest

4. **Serviço UsuarioAdminService**:
   - Manipular o campo nos métodos criar, editar e entityToDTO

Após essas alterações, o campo estará disponível no endpoint GET e poderá ser atualizado via PUT/POST.