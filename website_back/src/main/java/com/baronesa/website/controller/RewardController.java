package com.baronesa.website.controller;

import com.baronesa.website.dto.RewardWithCustomerName;
import com.baronesa.website.entity.Reward;
import com.baronesa.website.security.CustomUserPrincipal;
import com.baronesa.website.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    @GetMapping("/my")
    public ResponseEntity<List<Reward>> getMyRewards() {
        try {
            // Obter o usuário autenticado
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                Long userId = Long.parseLong(principal.getUserId());
                List<Reward> rewards = rewardService.listRewardsByUser(userId);
                return ResponseEntity.ok(rewards);
            } else {
                return ResponseEntity.status(401).build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/redeem")
    public ResponseEntity<String> redeemReward(@PathVariable Long id) {
        try {
            // Verificar se o usuário está autenticado e tem role ADMIN/SYSTEM/FUNCIONARIO
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body("Usuário não autenticado");
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                if (!principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_SYSTEM") && !principal.hasRole("ROLE_FUNCIONARIO")) {
                    return ResponseEntity.status(403).body("Acesso negado: permissão insuficiente");
                }
            } else {
                return ResponseEntity.status(401).body("Token inválido");
            }

            boolean success = rewardService.redeemReward(id);

            if (success) {
                return ResponseEntity.ok("Recompensa resgatada com sucesso");
            } else {
                return ResponseEntity.badRequest().body("Recompensa inválida ou já resgatada");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao resgatar recompensa: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Reward>> getRewardsByUser(@PathVariable Long userId) {
        try {
            // Verificar se o usuário está autenticado e tem role ADMIN/SYSTEM/FUNCIONARIO
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                if (!principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_SYSTEM") && !principal.hasRole("ROLE_FUNCIONARIO")) {
                    return ResponseEntity.status(403).build();
                }
            } else {
                return ResponseEntity.status(401).build();
            }

            List<Reward> rewards = rewardService.listRewardsByUser(userId);
            return ResponseEntity.ok(rewards);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/draw")
    public ResponseEntity<?> drawReward(@RequestBody DrawRewardRequest request) {
        try {
            // Verificar se o usuário está autenticado e tem role ADMIN/SYSTEM/FUNCIONARIO
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body("Usuário não autenticado");
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                if (!principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_SYSTEM") && !principal.hasRole("ROLE_FUNCIONARIO")) {
                    return ResponseEntity.status(403).body("Acesso negado: permissão insuficiente");
                }
            } else {
                return ResponseEntity.status(401).body("Token inválido");
            }

            com.baronesa.website.entity.Reward reward = rewardService.drawAndCreateReward(
                request.getTitle(),
                request.getDescription(),
                request.getImageUrl(),
                request.getValidUntil()
            );

            return ResponseEntity.ok(reward);
        } catch (RuntimeException e) {
            if ("Nenhum usuário elegível encontrado para sorteio".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body("Nenhum usuário elegível encontrado para sorteio");
            }
            return ResponseEntity.badRequest().body("Erro ao realizar sorteio: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao realizar sorteio: " + e.getMessage());
        }
    }

    public static class DrawRewardRequest {
        private String title;
        private String description;
        private String imageUrl;
        private java.time.LocalDateTime validUntil;

        // Getters e setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public java.time.LocalDateTime getValidUntil() {
            return validUntil;
        }

        public void setValidUntil(java.time.LocalDateTime validUntil) {
            this.validUntil = validUntil;
        }
    }

    @GetMapping("/with-customer-names")
    public ResponseEntity<List<RewardWithCustomerName>> getAllRewardsWithCustomerNames() {
        try {
            // Verificar se o usuário está autenticado e tem role ADMIN/SYSTEM/FUNCIONARIO
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                if (!principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_SYSTEM") && !principal.hasRole("ROLE_FUNCIONARIO")) {
                    return ResponseEntity.status(403).build();
                }
            } else {
                return ResponseEntity.status(401).build();
            }

            List<RewardWithCustomerName> rewards = rewardService.listAllRewardsWithCustomerNames();
            return ResponseEntity.ok(rewards);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}