package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.GrupoCliente;
import com.baronesa.emporio.entity.PerfilCliente;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.TipoPessoa;
import com.baronesa.emporio.repository.ClienteRepository;
import com.baronesa.emporio.repository.GrupoClienteRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final GrupoClienteRepository grupoClienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final String GRUPO_CLIENTE_ONLINE = "E-COMMERCE"; // Nome do grupo padrão para clientes online
    private static final int SENHA_TAMANHO = 8;
    private static final String CARACTERES_SENHA = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";


    @Transactional
    public void criar(ClienteRequest request) {
        log.info("Iniciando criação de cliente: {}", request.nome());

        // Validar email único
        log.debug("Verificando se email já existe: {}", request.email());
        if (usuarioRepository.existsByEmail(request.email())) {
            log.error("Email já cadastrado: {}", request.email());
            throw new RuntimeException("Email já cadastrado");
        }

        // Gerar senha automática se não fornecida (clientes de loja física)
        String senha = request.senha();
        boolean senhaGerada = false;
        if (senha == null || senha.isBlank()) {
            senha = PasswordGenerator.generate();
            senhaGerada = true;
            log.info("Senha automática gerada para cliente de loja física: {}", request.email());
        }

        // Criar usuário com Set mutável
        log.debug("Criando novo usuário");
        Set<Usuario.Role> roles = new HashSet<>();
        roles.add(Usuario.Role.CLIENTE);

        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .telefone(request.telefone())
                .senha(passwordEncoder.encode(senha))
                .ativo(request.ativo() != null ? request.ativo() : true)
                .roles(roles)  // Usando HashSet mutável
                .build();

        log.debug("Salvando usuário no banco");
        usuario = usuarioRepository.save(usuario);
        log.info("Usuário salvo com ID: {}", usuario.getId());

        // Criar perfil cliente
        log.debug("Criando perfil cliente");
        PerfilCliente perfilCliente = PerfilCliente.builder()
                .usuario(usuario)
                .tipoPessoa(request.tipoPessoa() != null ? request.tipoPessoa() : com.baronesa.emporio.enums.TipoPessoa.PF)
                .cpf(request.cpf())
                .cnpj(request.cnpj())
                .inscricaoEstadual(request.inscricaoEstadual())
                .dataNascimento(request.dataNascimento())
                .endereco(request.endereco())
                .logradouro(request.logradouro())
                .numero(request.numero())
                .bairro(request.bairro())
                .complemento(request.complemento())
                .cidade(request.cidade())
                .estado(request.estado())
                .cep(request.cep())
                .codigoMunicipioIbge(request.codigoMunicipioIbge())
                .origemCadastro(request.origemCadastro() != null ? request.origemCadastro() : com.baronesa.emporio.enums.OrigemCadastro.LOJA_FISICA)
                .build();

        // Associar grupo se informado
        if (request.grupoClienteId() != null) {
            log.debug("Associando grupo cliente ID: {}", request.grupoClienteId());
            GrupoCliente grupo = grupoClienteRepository.findById(request.grupoClienteId())
                    .orElseThrow(() -> {
                        log.error("Grupo de cliente não encontrado: {}", request.grupoClienteId());
                        return new RuntimeException("Grupo de cliente não encontrado");
                    });
            perfilCliente.setGrupoCliente(grupo);
        }

        usuario.setPerfilCliente(perfilCliente);

        log.debug("Atualizando usuário com perfil cliente");
        usuarioRepository.save(usuario);
        log.info("Cliente criado com sucesso - ID: {}", usuario.getId());
    }

    @Transactional
    public void editar(Long id, ClienteUpdateRequest request) {
        log.info("Iniciando edição de cliente ID: {}", id);

        Usuario usuario = clienteRepository.findByIdWithPerfilCliente(id, Usuario.Role.CLIENTE)
                .orElseThrow(() -> {
                    log.error("Cliente não encontrado: {}", id);
                    return new RuntimeException("Cliente não encontrado");
                });

        // Validar email único (exceto para o próprio cliente)
        if (!usuario.getEmail().equals(request.email())) {
            log.debug("Email mudou de {} para {}, verificando unicidade", usuario.getEmail(), request.email());
            if (usuarioRepository.existsByEmail(request.email())) {
                log.error("Email já cadastrado: {}", request.email());
                throw new RuntimeException("Email já cadastrado");
            }
        }

        // Atualizar dados do usuário
        log.debug("Atualizando dados do usuário");
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        if (request.roles() != null) {
            usuario.setRoles(new HashSet<>(request.roles()));
        }
        usuario.setTelefone(request.telefone());
        usuario.setAtivo(request.ativo());

        // Garantir que tem perfil cliente
        if (usuario.getPerfilCliente() == null) {
            log.warn("Usuário sem perfil cliente, criando novo perfil");
            usuario.setPerfilCliente(PerfilCliente.builder()
                    .usuario(usuario)
                    .build());
        }

        // Atualizar perfil cliente
        log.debug("Atualizando perfil cliente");
        PerfilCliente perfil = usuario.getPerfilCliente();
        
        // Atualizar dados de identificação
        if (request.tipoPessoa() != null) {
            perfil.setTipoPessoa(request.tipoPessoa());
        }
        perfil.setCpf(request.cpf());
        perfil.setCnpj(request.cnpj());
        perfil.setInscricaoEstadual(request.inscricaoEstadual());
        perfil.setDataNascimento(request.dataNascimento());
        
        // Atualizar endereço (campos existentes)
        perfil.setEndereco(request.endereco());
        perfil.setCidade(request.cidade());
        perfil.setEstado(request.estado());
        perfil.setCep(request.cep());
        
        // Atualizar endereço (campos detalhados)
        perfil.setLogradouro(request.logradouro());
        perfil.setNumero(request.numero());
        perfil.setBairro(request.bairro());
        perfil.setComplemento(request.complemento());
        perfil.setCodigoMunicipioIbge(request.codigoMunicipioIbge());
        
        // Atualizar metadados
        if (request.origemCadastro() != null) {
            perfil.setOrigemCadastro(request.origemCadastro());
        }
        if (request.mensalista() != null) {
            perfil.setMensalista(request.mensalista());
        }

        // Atualizar grupo
        if (request.grupoClienteId() != null) {
            log.debug("Atualizando grupo cliente para ID: {}", request.grupoClienteId());
            GrupoCliente grupo = grupoClienteRepository.findById(request.grupoClienteId())
                    .orElseThrow(() -> {
                        log.error("Grupo de cliente não encontrado: {}", request.grupoClienteId());
                        return new RuntimeException("Grupo de cliente não encontrado");
                    });
            perfil.setGrupoCliente(grupo);
        } else {
            log.debug("Removendo grupo cliente");
            perfil.setGrupoCliente(null);
        }

        log.debug("Salvando alterações do cliente");
        usuarioRepository.save(usuario);
        log.info("Cliente ID {} atualizado com sucesso", id);
    }

    public ClienteDTO buscarPorId(Long id) {
        Usuario usuario = clienteRepository.findByIdWithPerfilCliente(id, Usuario.Role.CLIENTE)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        return entityToDTO(usuario);
    }

    public List<ClienteOptionDTO> listarOptions() {
        return clienteRepository.findByRole(Usuario.Role.CLIENTE).stream()
                .map(u -> new ClienteOptionDTO(u.getId(), u.getNome(), u.getTelefone(), u.getEmail()))
                .toList();
    }

    /**
     * Busca dados do cliente para checkout baseado no ID do usuário autenticado
     * @param usuarioId ID do usuário autenticado
     * @return DTO com todos os dados necessários para checkout
     */
    @Transactional(readOnly = true)
    public ClienteCheckoutDTO buscarClienteParaCheckout(Long usuarioId) {
        log.info("Buscando dados do cliente para checkout - Usuario ID: {}", usuarioId);

        Usuario usuario = clienteRepository.findByIdWithPerfilCliente(usuarioId, Usuario.Role.CLIENTE)
                .orElseThrow(() -> {
                    log.error("Cliente não encontrado para usuário ID: {}", usuarioId);
                    return new RuntimeException("Cliente não encontrado");
                });

        PerfilCliente perfil = usuario.getPerfilCliente();

        // Se não tem perfil cliente ainda, criar DTO com dados básicos
        if (perfil == null) {
            log.warn("Usuário {} não possui perfil cliente completo", usuarioId);
            return new ClienteCheckoutDTO(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    TipoPessoa.PF, // Default
                    null, // cpf
                    null, // cnpj
                    null, // inscricaoEstadual
                    null, // logradouro
                    null, // numero
                    null, // complemento
                    null, // bairro
                    null, // cidade
                    null, // estado
                    null, // cep
                    null, // codigoMunicipioIbge
                    false, // perfilCompleto
                    usuario.getEmailVerificado()
            );
        }

        // Criar DTO baseado no tipo de pessoa
        if (perfil.getTipoPessoa() == TipoPessoa.PF) {
            return ClienteCheckoutDTO.criarPessoaFisica(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    perfil.getCpf(),
                    perfil.getLogradouro(),
                    perfil.getNumero(),
                    perfil.getComplemento(),
                    perfil.getBairro(),
                    perfil.getCidade(),
                    perfil.getEstado(),
                    perfil.getCep(),
                    perfil.getCodigoMunicipioIbge(),
                    perfil.isPerfilCompleto(),
                    usuario.getEmailVerificado()
            );
        } else {
            return ClienteCheckoutDTO.criarPessoaJuridica(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    perfil.getCnpj(),
                    perfil.getInscricaoEstadual(),
                    perfil.getLogradouro(),
                    perfil.getNumero(),
                    perfil.getComplemento(),
                    perfil.getBairro(),
                    perfil.getCidade(),
                    perfil.getEstado(),
                    perfil.getCep(),
                    perfil.getCodigoMunicipioIbge(),
                    perfil.isPerfilCompleto(),
                    usuario.getEmailVerificado()
            );
        }
    }

    @Transactional
    public void deletar(Long id) {
        log.info("Iniciando exclusão de cliente ID: {}", id);

        Usuario usuario = clienteRepository.findByIdAndRole(id, Usuario.Role.CLIENTE)
                .orElseThrow(() -> {
                    log.error("Cliente não encontrado para exclusão: {}", id);
                    return new RuntimeException("Cliente não encontrado");
                });

        log.debug("Excluindo usuário: {}", usuario.getEmail());
        usuarioRepository.delete(usuario);
        log.info("Cliente ID {} excluído com sucesso", id);
    }

    @Transactional
    public void alterarSenha(Long id, String novaSenha) {
        Usuario usuario = clienteRepository.findByIdAndRole(id, Usuario.Role.CLIENTE)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
    }

    private ClienteDTO entityToDTO(Usuario usuario) {
        PerfilCliente perfil = usuario.getPerfilCliente();

        return new ClienteDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getAtivo(),
                usuario.getEmailVerificado(),
                perfil != null && perfil.getGrupoCliente() != null ? perfil.getGrupoCliente().getId() : null,
                perfil != null && perfil.getGrupoCliente() != null ? perfil.getGrupoCliente().getDescricao() : null,
                perfil != null ? perfil.getTipoPessoa() : null,
                perfil != null ? perfil.getCpf() : null,
                perfil != null ? perfil.getCnpj() : null,
                perfil != null ? perfil.getInscricaoEstadual() : null,
                perfil != null ? perfil.getDataNascimento() : null,
                perfil != null ? perfil.getEndereco() : null,
                perfil != null ? perfil.getLogradouro() : null,
                perfil != null ? perfil.getNumero() : null,
                perfil != null ? perfil.getComplemento() : null,
                perfil != null ? perfil.getBairro() : null,
                perfil != null ? perfil.getCidade() : null,
                perfil != null ? perfil.getEstado() : null,
                perfil != null ? perfil.getCep() : null,
                perfil != null ? perfil.getCodigoMunicipioIbge() : null,
                perfil != null ? perfil.getMensalista() : null,
                perfil != null ? perfil.getOrigemCadastro() : null,
                usuario.getCriadoEm(),
                usuario.getUltimoLogin()
        );
    }

    /**
     * Busca dados do cliente pelo email para checkout sem login
     * @param email Email do cliente
     * @return DTO com dados do cliente se existir
     */
    @Transactional(readOnly = true)
    public ClienteCheckoutDTO buscarClientePorEmailParaCheckout(String email) {
        log.info("Buscando dados do cliente por email para checkout: {}", email);
        
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        if (usuarioOpt.isEmpty()) {
            log.info("Cliente não encontrado com email: {}", email);
            return null;
        }
        
        Usuario usuario = usuarioOpt.get();
        
        // Verificar se é cliente
        if (!usuario.getRoles().contains(Usuario.Role.CLIENTE)) {
            log.warn("Usuário com email {} não é cliente", email);
            return null;
        }
        
        PerfilCliente perfil = usuario.getPerfilCliente();
        log.info("Cliente encontrado - ID: {}, Email: {}", usuario.getId(), email);
        
        // Se não tem perfil, retornar dados básicos
        if (perfil == null) {
            log.warn("Cliente {} não possui perfil completo", email);
            return new ClienteCheckoutDTO(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    TipoPessoa.PF, // Default
                    null, // cpf
                    null, // cnpj
                    null, // inscricaoEstadual
                    null, // logradouro
                    null, // numero
                    null, // complemento
                    null, // bairro
                    null, // cidade
                    null, // estado
                    null, // cep
                    null, // codigoMunicipioIbge
                    false, // perfilCompleto
                    usuario.getEmailVerificado()
            );
        }
        
        // Criar DTO baseado no tipo de pessoa
        if (perfil.getTipoPessoa() == TipoPessoa.PF) {
            return ClienteCheckoutDTO.criarPessoaFisica(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    perfil.getCpf(),
                    perfil.getLogradouro(),
                    perfil.getNumero(),
                    perfil.getComplemento(),
                    perfil.getBairro(),
                    perfil.getCidade(),
                    perfil.getEstado(),
                    perfil.getCep(),
                    perfil.getCodigoMunicipioIbge(),
                    perfil.isPerfilCompleto(),
                    usuario.getEmailVerificado()
            );
        } else {
            return ClienteCheckoutDTO.criarPessoaJuridica(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    perfil.getCnpj(),
                    perfil.getInscricaoEstadual(),
                    perfil.getLogradouro(),
                    perfil.getNumero(),
                    perfil.getComplemento(),
                    perfil.getBairro(),
                    perfil.getCidade(),
                    perfil.getEstado(),
                    perfil.getCep(),
                    perfil.getCodigoMunicipioIbge(),
                    perfil.isPerfilCompleto(),
                    usuario.getEmailVerificado()
            );
        }
    }

    // ========================================================================
    // MÉTODOS DE E-COMMERCE COMENTADOS - NÃO APLICÁVEIS AO SISTEMA DE BARES
    // ========================================================================

    /*
    @Transactional
    public ClienteVendaOnlineResult buscarOuCriarCliente(ClienteVendaOnlineDTO dadosCliente) {
        log.info("Buscando/criando cliente para venda online: {}", dadosCliente.email());

        Optional<Usuario> clienteExistente = usuarioRepository.findByEmail(dadosCliente.email());

        if (clienteExistente.isPresent()) {
            log.info("Cliente já existe com ID: {}", clienteExistente.get().getId());
            return new ClienteVendaOnlineResult(clienteExistente.get(), null, false);
        }

        Optional<Usuario> clientePorCpf = clienteRepository.findByCpf(dadosCliente.cpf());

        if (clientePorCpf.isPresent()) {
            log.info("Cliente encontrado por CPF com ID: {}", clientePorCpf.get().getId());
            Usuario cliente = clientePorCpf.get();
            if (!cliente.getEmail().equals(dadosCliente.email())) {
                log.info("Atualizando email do cliente de {} para {}",
                        cliente.getEmail(), dadosCliente.email());
                cliente.setEmail(dadosCliente.email());
                usuarioRepository.save(cliente);
            }
            return new ClienteVendaOnlineResult(cliente, null, false);
        }

        log.info("Cliente não encontrado. Criando novo cliente...");
        String senhaTemporaria = gerarSenhaAleatoria();
        Usuario novoCliente = criarClienteVendaOnline(dadosCliente, senhaTemporaria);

        try {
            emailService.sendWelcomeEmail(
                    novoCliente.getEmail(),
                    novoCliente.getNome(),
                    senhaTemporaria
            );
            log.info("Email de boas-vindas enviado para: {}", novoCliente.getEmail());
        } catch (Exception e) {
            log.error("Erro ao enviar email de boas-vindas: {}", e.getMessage());
        }

        return new ClienteVendaOnlineResult(novoCliente, senhaTemporaria, true);
    }

    private Usuario criarClienteVendaOnline(ClienteVendaOnlineDTO dados, String senha) {
        Set<Usuario.Role> roles = new HashSet<>();
        roles.add(Usuario.Role.CLIENTE);

        Usuario usuario = Usuario.builder()
                .nome(dados.nome())
                .email(dados.email())
                .telefone(dados.telefone())
                .senha(passwordEncoder.encode(senha))
                .ativo(true)
                .emailVerificado(false)
                .roles(roles)
                .clienteOnline(true)
                .build();

        usuario = usuarioRepository.save(usuario);
        log.info("Usuário criado com ID: {}", usuario.getId());

        PerfilCliente perfilCliente = PerfilCliente.builder()
                .usuario(usuario)
                .cpf(dados.cpf())
                .tipoPessoa(TipoPessoa.PF)
                .logradouro(dados.logradouro())
                .numero(dados.numero())
                .complemento(dados.complemento())
                .bairro(dados.bairro())
                .cidade(dados.cidade())
                .estado(dados.estado())
                .cep(dados.cep())
                .build();

        grupoClienteRepository.findByDescricao(GRUPO_CLIENTE_ONLINE)
                .ifPresent(perfilCliente::setGrupoCliente);

        usuario.setPerfilCliente(perfilCliente);
        usuario = usuarioRepository.save(usuario);

        log.info("Cliente online criado com sucesso - ID: {}", usuario.getId());
        return usuario;
    }

    @Transactional
    public void atualizarDadosCliente(Long clienteId, ClienteVendaOnlineDTO dadosAtualizados) {
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        boolean atualizado = false;

        if ((cliente.getTelefone() == null || cliente.getTelefone().isEmpty())
                && dadosAtualizados.telefone() != null) {
            cliente.setTelefone(dadosAtualizados.telefone());
            atualizado = true;
        }

        PerfilCliente perfil = cliente.getPerfilCliente();
        if (perfil != null) {
            if (isEnderecoVazio(perfil) && !isEnderecoVazio(dadosAtualizados)) {
                perfil.setLogradouro(dadosAtualizados.logradouro());
                perfil.setNumero(dadosAtualizados.numero());
                perfil.setComplemento(dadosAtualizados.complemento());
                perfil.setBairro(dadosAtualizados.bairro());
                perfil.setCidade(dadosAtualizados.cidade());
                perfil.setEstado(dadosAtualizados.estado());
                perfil.setCep(dadosAtualizados.cep());
                atualizado = true;
            }
        }

        if (atualizado) {
            usuarioRepository.save(cliente);
            log.info("Dados do cliente {} atualizados", clienteId);
        }
    }

    private String gerarSenhaAleatoria() {
        SecureRandom random = new SecureRandom();
        StringBuilder senha = new StringBuilder(SENHA_TAMANHO);

        for (int i = 0; i < SENHA_TAMANHO; i++) {
            int index = random.nextInt(CARACTERES_SENHA.length());
            senha.append(CARACTERES_SENHA.charAt(index));
        }

        return senha.toString();
    }

    private boolean isPerfilCompleto(ClienteVendaOnlineDTO dados) {
        return dados.nome() != null && !dados.nome().isEmpty() &&
                dados.email() != null && !dados.email().isEmpty() &&
                dados.cpf() != null && !dados.cpf().isEmpty() &&
                dados.logradouro() != null && !dados.logradouro().isEmpty() &&
                dados.numero() != null && !dados.numero().isEmpty() &&
                dados.bairro() != null && !dados.bairro().isEmpty() &&
                dados.cidade() != null && !dados.cidade().isEmpty() &&
                dados.estado() != null && !dados.estado().isEmpty() &&
                dados.cep() != null && !dados.cep().isEmpty();
    }

    private boolean isEnderecoVazio(ClienteVendaOnlineDTO dados) {
        return dados.logradouro() == null || dados.logradouro().isEmpty();
    }

    public record ClienteVendaOnlineResult(
            Usuario cliente,
            String senhaGerada,
            boolean clienteNovo
    ) {}
    */

    /**
     * Verifica se o endereço está vazio
     */
    private boolean isEnderecoVazio(PerfilCliente perfil) {
        return perfil.getLogradouro() == null || perfil.getLogradouro().isEmpty();
    }
}