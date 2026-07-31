package com.baronesa.emporio.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baronesa.emporio.entity.Notification;
import com.baronesa.emporio.entity.NotificationRecipient;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.NotificationRecipientRepository;
import com.baronesa.emporio.repository.NotificationRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final Usuario.Role EXCLUDED_ROLE = Usuario.Role.CLIENTE;

    // ========== MÉTODOS CRUD BÁSICOS ==========

    public Page<Notification> findAll(Pageable pageable, String search, String tipo,
                                      String importancia, Boolean ativo) {
        Specification<Notification> spec = Specification.where(null);

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> 
                cb.or(
                    cb.like(cb.lower(root.get("titulo")), "%" + search.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("mensagem")), "%" + search.toLowerCase() + "%")
                )
            );
        }

        if (tipo != null && !tipo.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("tipo"), tipo)
            );
        }

        if (importancia != null && !importancia.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("importancia"), importancia)
            );
        }

        if (ativo != null) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("ativo"), ativo)
            );
        }

        return notificationRepository.findAll(spec, pageable);
    }

    public Optional<Notification> findById(Long id) {
        return notificationRepository.findById(id);
    }
    
    public Page<Map<String, Object>> findRecentActivities(Pageable pageable, List<String> tiposAtividades) {
        try {
            int limit = pageable.getPageSize();
            
            List<Object[]> results = notificationRepository.findRecentActivitiesByTitles(
                tiposAtividades, "ADMIN", limit);
            
            List<Map<String, Object>> activities = convertToRecentActivitiesMaps(results);
            
            return new PageImpl<>(activities, pageable, activities.size());
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar atividades recentes: " + e.getMessage(), e);
        }
    }
    
    // Converter atividades recentes
    private List<Map<String, Object>> convertToRecentActivitiesMaps(List<Object[]> results) {
        return results.stream().map(result -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", result[0]);
            map.put("titulo", result[1]);
            map.put("mensagem", result[2]);
            map.put("tipo", result[3]);
            map.put("importancia", result[4]);
            map.put("dataCriacao", result[5]);
            map.put("dataExpiracao", result[6]);
            map.put("link", result[7]);
            map.put("createdByName", result[8]);
            map.put("lido", false);
            map.put("readAt", null);
            return map;
        }).collect(Collectors.toList());
    }    

    @Transactional
    public Notification create(Map<String, Object> notificationData) {
        try {
            Notification notification = new Notification();
            
            notification.setTitulo((String) notificationData.get("titulo"));
            notification.setMensagem((String) notificationData.get("mensagem"));
            notification.setTipo((String) notificationData.get("tipo"));
            notification.setImportancia((String) notificationData.get("importancia"));
            notification.setDataCriacao(Timestamp.from(Instant.now()));
            
            if (notificationData.containsKey("link")) {
                notification.setLink((String) notificationData.get("link"));
            }
            
            if (notificationData.containsKey("dataExpiracao") && 
                notificationData.get("dataExpiracao") != null) {
                String dataExp = (String) notificationData.get("dataExpiracao");
                if (!dataExp.trim().isEmpty()) {
                    notification.setDataExpiracao(dataExp);
                }
            }
            
            // Definir criador
            Long createdById = getCreatedById(notificationData);
            if (createdById != null) {
                notification.setCreatedBy(createdById);
            }

            // Processar destinatários
            processDestinationData(notification, notificationData);

            notification = notificationRepository.save(notification);
            createRecipients(notification);

            return notification;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar notificação: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Optional<Notification> update(Long id, Map<String, Object> notificationData) {
        Optional<Notification> notificationOpt = notificationRepository.findById(id);
        if (!notificationOpt.isPresent()) {
            return Optional.empty();
        }
        
        Notification notification = notificationOpt.get();
        try {
            notification.setTitulo((String) notificationData.get("titulo"));
            notification.setMensagem((String) notificationData.get("mensagem"));
            notification.setImportancia((String) notificationData.get("importancia"));
            
            if (notificationData.containsKey("link")) {
                notification.setLink((String) notificationData.get("link"));
            }
            
            if (notificationData.containsKey("dataExpiracao")) {
                String dataExp = (String) notificationData.get("dataExpiracao");
                notification.setDataExpiracao(dataExp != null && !dataExp.trim().isEmpty() ? dataExp : null);
            }
            
            if (notificationData.containsKey("ativo")) {
                notification.setAtivo((Boolean) notificationData.get("ativo"));
            }

            String novoTipo = (String) notificationData.get("tipo");
            if (!notification.getTipo().equals(novoTipo)) {
                deleteRecipientsByNotificationId(notification.getId());
                notification.setTipo(novoTipo);
                notification.setUserId(null);
                notification.setRole(null);
                
                processDestinationData(notification, notificationData);
                notification = notificationRepository.save(notification);
                createRecipients(notification);
            } else {
                notification = notificationRepository.save(notification);
            }

            return Optional.of(notification);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao atualizar notificação: " + e.getMessage(), e);
        }
    }

    @Transactional
    public boolean delete(Long id) {
        try {
            deleteRecipientsByNotificationId(id);
            notificationRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao excluir notificação: " + e.getMessage(), e);
        }
    }

    // ========== MÉTODOS DE BUSCA PARA USUÁRIO ==========

    public Page<Map<String, Object>> findNotificationsForUser(Long userId, Pageable pageable, boolean includeRead) {
        Usuario user = getUserAndValidate(userId);
        if (user.getRoles().contains(EXCLUDED_ROLE)) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
        
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        
        List<Map<String, Object>> allNotifications = new ArrayList<>();
        
        // 1. Buscar notificações básicas (GERAL e INDIVIDUAL)
        List<Object[]> basicResults = includeRead ? 
            notificationRepository.findNotificationsForUserIncludingRead(userId, limit * 2, 0) :
            notificationRepository.findUnreadNotificationsForUser(userId, limit * 2, 0);
        
        allNotifications.addAll(convertToNotificationMaps(basicResults));
        
        // 2. Buscar notificações por ROLE
        for (Usuario.Role role : user.getRoles()) {
            if (!EXCLUDED_ROLE.equals(role)) {
                List<Object[]> roleResults = notificationRepository.findNotificationsByRole(role.name());
                for (Object[] roleResult : roleResults) {
                    Map<String, Object> roleNotification = convertRoleResultToMap(roleResult, user, includeRead);
                    if (roleNotification != null && !isDuplicate(allNotifications, roleNotification)) {
                        allNotifications.add(roleNotification);
                    }
                }
            }
        }
        
        // 3. Ordenar e paginar
        allNotifications.sort(this::compareNotifications);
        
        int start = Math.min(offset, allNotifications.size());
        int end = Math.min(start + limit, allNotifications.size());
        List<Map<String, Object>> paginatedResult = allNotifications.subList(start, end);
        
        return new PageImpl<>(paginatedResult, pageable, allNotifications.size());
    }

    public Long countUnreadNotifications(Long userId) {
        Usuario user = getUserAndValidate(userId);
        if (user.getRoles().contains(EXCLUDED_ROLE)) {
            return 0L;
        }
        
        // Contar básicas não lidas
        Long basicCount = notificationRepository.countUnreadNotificationsForUser(userId);
        
        // Contar por role não lidas
        Long roleCount = 0L;
        for (Usuario.Role role : user.getRoles()) {
            if (!EXCLUDED_ROLE.equals(role)) {
                List<Object[]> roleResults = notificationRepository.findNotificationsByRole(role.name());
                for (Object[] result : roleResults) {
                    Long notifId = ((Number) result[0]).longValue();
                    if (!isNotificationReadByUser(notifId, user)) {
                        roleCount++;
                    }
                }
            }
        }
        
        return basicCount + roleCount;
    }

    // ========== MÉTODOS DE INTERAÇÃO ==========

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Usuario user = getUserAndValidate(userId);
        validateUserCanInteract(user);
        
        Notification notification = getNotificationAndValidate(notificationId);
        
        Optional<NotificationRecipient> recipientOpt =
            notificationRecipientRepository.findByNotificationAndUser(notification, user);
        
        if (recipientOpt.isPresent()) {
            NotificationRecipient nr = recipientOpt.get();
            nr.setReadAt(LocalDateTime.now());
            notificationRecipientRepository.save(nr);
        } else {
            NotificationRecipient newRecipient = NotificationRecipient.builder()
                .notification(notification)
                .user(user)
                .readAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
            notificationRecipientRepository.save(newRecipient);
        }
    }

    @Transactional
    public void markAsDeleted(Long notificationId, Long userId) {
        Usuario user = getUserAndValidate(userId);
        validateUserCanInteract(user);
        
        Notification notification = getNotificationAndValidate(notificationId);
        
        Optional<NotificationRecipient> recipientOpt = 
            notificationRecipientRepository.findByNotificationAndUser(notification, user);
        
        if (recipientOpt.isPresent()) {
            NotificationRecipient nr = recipientOpt.get();
            nr.setDeletedAt(LocalDateTime.now());
            notificationRecipientRepository.save(nr);
        } else {
            NotificationRecipient newRecipient = NotificationRecipient.builder()
                .notification(notification)
                .user(user)
                .deletedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
            notificationRecipientRepository.save(newRecipient);
        }
    }

    // ========== MÉTODOS AUXILIARES ==========

    public List<String> getAvailableRoles() {
        List<String> roles = new ArrayList<>();
        roles.add("ADMIN");
        roles.add("ATENDENTE");
        return roles;
    }

    public List<Map<String, Object>> getAvailableUsers() {
        List<Usuario> users = usuarioRepository.findByAtivoTrueAndRolesNotContaining(EXCLUDED_ROLE);
        
        return users.stream().map(user -> {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("name", user.getNome());
            userMap.put("email", user.getEmail());
            userMap.put("roles", user.getRoles().stream().map(Usuario.Role::name).collect(Collectors.toList()));
            return userMap;
        }).collect(Collectors.toList());
    }

    // ========== MÉTODOS PRIVADOS ==========

    private Long getCreatedById(Map<String, Object> notificationData) {
        if (notificationData.containsKey("createdById")) {
            return Long.valueOf(notificationData.get("createdById").toString());
        }
        
        // Buscar por email (configuração atual do sistema)
        Optional<Usuario> adminUser = usuarioRepository.findByEmail("greggorio@gmail.com");
        return adminUser.map(Usuario::getId).orElse(null);
    }

    private void processDestinationData(Notification notification, Map<String, Object> notificationData) {
        String tipo = notification.getTipo();
        
        if ("INDIVIDUAL".equals(tipo)) {
            Long userId = Long.valueOf(notificationData.get("userId").toString());
            Usuario user = getUserAndValidate(userId);
            validateUserCanReceiveNotifications(user);
            notification.setUserId(userId);
            
        } else if ("ROLE".equals(tipo)) {
            String role = (String) notificationData.get("role");
            validateRoleCanReceiveNotifications(role);
            notification.setRole(role);
        }
    }

    private void createRecipients(Notification notification) {
        String tipo = notification.getTipo();
        
        if ("INDIVIDUAL".equals(tipo) && notification.getUserId() != null) {
            createIndividualRecipient(notification);
        } else if ("ROLE".equals(tipo) && notification.getRole() != null) {
            createRoleRecipients(notification);
        } else if ("GERAL".equals(tipo)) {
            createGeneralRecipients(notification);
        }
    }

    private void createIndividualRecipient(Notification notification) {
        Optional<Usuario> userOpt = usuarioRepository.findById(notification.getUserId());
        if (userOpt.isPresent() && !userOpt.get().getRoles().contains(EXCLUDED_ROLE)) {
            NotificationRecipient recipient = NotificationRecipient.builder()
                .notification(notification)
                .user(userOpt.get())
                .createdAt(LocalDateTime.now())
                .build();
            notificationRecipientRepository.save(recipient);
        }
    }

    private void createRoleRecipients(Notification notification) {
        String roleString = notification.getRole();
        if (!EXCLUDED_ROLE.name().equals(roleString)) {
            try {
                Usuario.Role role = Usuario.Role.valueOf(roleString);
                List<Usuario> usersWithRole = usuarioRepository.findByRolesContaining(role);
                for (Usuario user : usersWithRole) {
                    if (!user.getRoles().contains(EXCLUDED_ROLE)) {
                        NotificationRecipient recipient = NotificationRecipient.builder()
                            .notification(notification)
                            .user(user)
                            .role(roleString)
                            .createdAt(LocalDateTime.now())
                            .build();
                        notificationRecipientRepository.save(recipient);
                    }
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Role inválida: " + roleString);
            }
        }
    }

    private void createGeneralRecipients(Notification notification) {
        List<Usuario> allActiveUsers = usuarioRepository.findByAtivoTrue();
        for (Usuario user : allActiveUsers) {
            if (!user.getRoles().contains(EXCLUDED_ROLE)) {
                NotificationRecipient recipient = NotificationRecipient.builder()
                    .notification(notification)
                    .user(user)
                    .createdAt(LocalDateTime.now())
                    .build();
                notificationRecipientRepository.save(recipient);
            }
        }
    }

    private void deleteRecipientsByNotificationId(Long notificationId) {
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
        if (notificationOpt.isPresent()) {
            List<NotificationRecipient> recipients = 
                notificationRecipientRepository.findByNotification(notificationOpt.get());
            notificationRecipientRepository.deleteAll(recipients);
        }
    }

    private List<Map<String, Object>> convertToNotificationMaps(List<Object[]> results) {
        return results.stream().map(result -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", result[0]);
            map.put("titulo", result[1]);
            map.put("mensagem", result[2]);
            map.put("tipo", result[3]);
            map.put("importancia", result[4]);
            map.put("dataCriacao", result[5]);
            map.put("dataExpiracao", result[6]);
            map.put("link", result[7]);
            map.put("createdByName", result[8]);
            map.put("lido", result[9] != null);
            map.put("readAt", result[9]);
            return map;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> convertRoleResultToMap(Object[] result, Usuario user, boolean includeRead) {
        Long notifId = ((Number) result[0]).longValue();
        boolean isRead = isNotificationReadByUser(notifId, user);
        
        if (!includeRead && isRead) {
            return null;
        }
        
        Map<String, Object> map = new HashMap<>();
        map.put("id", result[0]);
        map.put("titulo", result[1]);
        map.put("mensagem", result[2]);
        map.put("tipo", result[3]);
        map.put("importancia", result[4]);
        map.put("dataCriacao", result[5]);
        map.put("dataExpiracao", result[6]);
        map.put("link", result[7]);
        map.put("createdByName", result[8]);
        map.put("lido", isRead);
        map.put("readAt", getReadAtForUser(notifId, user));
        return map;
    }

    private boolean isDuplicate(List<Map<String, Object>> notifications, Map<String, Object> newNotification) {
        return notifications.stream()
            .anyMatch(existing -> existing.get("id").equals(newNotification.get("id")));
    }

    private int compareNotifications(Map<String, Object> a, Map<String, Object> b) {
        String importanciaA = (String) a.get("importancia");
        String importanciaB = (String) b.get("importancia");
        
        int prioA = getImportanciaPriority(importanciaA);
        int prioB = getImportanciaPriority(importanciaB);
        
        if (prioA != prioB) {
            return Integer.compare(prioA, prioB);
        }
        
        Timestamp dataA = (Timestamp) a.get("dataCriacao");
        Timestamp dataB = (Timestamp) b.get("dataCriacao");
        return dataB.compareTo(dataA);
    }

    private int getImportanciaPriority(String importancia) {
        switch (importancia) {
            case "URGENTE": return 1;
            case "ALTA": return 2;
            case "MEDIA": return 3;
            default: return 4;
        }
    }

    private boolean isNotificationReadByUser(Long notificationId, Usuario user) {
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
        if (!notificationOpt.isPresent()) return false;
        
        Optional<NotificationRecipient> recipient = 
            notificationRecipientRepository.findByNotificationAndUser(notificationOpt.get(), user);
        
        return recipient.isPresent() && recipient.get().getReadAt() != null;
    }

    private LocalDateTime getReadAtForUser(Long notificationId, Usuario user) {
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
        if (!notificationOpt.isPresent()) return null;
        
        Optional<NotificationRecipient> recipient = 
            notificationRecipientRepository.findByNotificationAndUser(notificationOpt.get(), user);
        
        return recipient.map(NotificationRecipient::getReadAt).orElse(null);
    }

    private Usuario getUserAndValidate(Long userId) {
        return usuarioRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    private Notification getNotificationAndValidate(Long notificationId) {
        return notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notificação não encontrada"));
    }

    private void validateUserCanInteract(Usuario user) {
        if (user.getRoles().contains(EXCLUDED_ROLE)) {
            throw new RuntimeException("Usuários com role CLIENTE não podem interagir com notificações");
        }
    }

    private void validateUserCanReceiveNotifications(Usuario user) {
        if (user.getRoles().contains(EXCLUDED_ROLE)) {
            throw new RuntimeException("Não é possível enviar notificações para usuários com role CLIENTE");
        }
    }

    private void validateRoleCanReceiveNotifications(String role) {
        if (EXCLUDED_ROLE.name().equals(role)) {
            throw new RuntimeException("Não é possível enviar notificações para usuários com role CLIENTE");
        }
    }

    /**
     * Verifica se existe notificação não lida contendo termos específicos
     */
    public boolean hasUnreadNotificationContaining(String titulo, String conteudo) {
        List<Notification> notifications = notificationRepository.findByTituloContainingAndMensagemContaining(titulo, conteudo);
        return notifications.stream().anyMatch(notification -> 
            notificationRecipientRepository.existsByNotificationAndReadAtIsNull(notification)
        );
    }
}