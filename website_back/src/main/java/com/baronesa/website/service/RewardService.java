package com.baronesa.website.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.baronesa.website.dto.RewardWithCustomerName;
import com.baronesa.website.entity.NotificationHistory;
import com.baronesa.website.entity.NotificationSubscription;
import com.baronesa.website.entity.Reward;
import com.baronesa.website.entity.RewardStatus;
import com.baronesa.website.repository.NotificationSubscriptionRepository;
import com.baronesa.website.repository.RewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {

    private final RewardRepository rewardRepository;
    private final NotificationService notificationService;
    private final NotificationSubscriptionRepository subscriptionRepository;

    @Transactional
    public Reward createReward(Long userId, String title, String description, String imageUrl, LocalDateTime validUntil) {
        Reward reward = new Reward();
        reward.setUserId(userId);
        reward.setTitle(title);
        reward.setDescription(description);
        reward.setImageUrl(imageUrl);
        reward.setValidUntil(validUntil);
        reward.setStatus(RewardStatus.AVAILABLE);
        // createdAt será preenchido automaticamente pelo @PrePersist
        reward.setRedeemedAt(null);
        reward.setNotificationHistoryId(null);

        return rewardRepository.save(reward);
    }

    public List<Reward> listRewardsByUser(Long userId) {
        return rewardRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public boolean redeemReward(Long rewardId) {
        Optional<Reward> rewardOpt = rewardRepository.findById(rewardId);

        if (rewardOpt.isEmpty()) {
            return false;
        }

        Reward reward = rewardOpt.get();

        if (reward.getStatus() != RewardStatus.AVAILABLE) {
            return false;
        }

        reward.setStatus(RewardStatus.REDEEMED);
        reward.setRedeemedAt(LocalDateTime.now());

        rewardRepository.save(reward);
        return true;
    }

    @Transactional
    public Reward drawAndCreateReward(String title, String description, String imageUrl, LocalDateTime validUntil)
            throws FirebaseMessagingException {

        // Buscar usuários elegíveis via NotificationSubscriptionRepository
        List<Long> eligibleUserIds = subscriptionRepository.findDistinctActiveUserIds();

        if (eligibleUserIds.isEmpty()) {
            throw new RuntimeException("Nenhum usuário elegível encontrado para sorteio");
        }

        // Sortear 1 userId aleatoriamente
        Random random = new Random();
        Long selectedUserId = eligibleUserIds.get(random.nextInt(eligibleUserIds.size()));

        log.info("Usuário sorteado para recompensa: {}", selectedUserId);

        // Criar Reward com o usuário sorteado
        Reward reward = new Reward();
        reward.setUserId(selectedUserId);
        reward.setTitle(title);
        reward.setDescription(description);
        reward.setImageUrl(imageUrl);
        reward.setValidUntil(validUntil);
        reward.setStatus(RewardStatus.AVAILABLE);
        // createdAt será preenchido automaticamente pelo @PrePersist
        reward.setRedeemedAt(null);
        reward.setNotificationHistoryId(null);

        Reward savedReward = rewardRepository.save(reward);

        // Disparar push apenas para o usuário sorteado
        // Primeiro, obter os tokens FCM do usuário sorteado
        List<NotificationSubscription> userSubscriptions = subscriptionRepository.findByUserId(selectedUserId);

        if (!userSubscriptions.isEmpty()) {
            // Criar payload JSON com os IDs relevantes (antes de enviar a notificação)
            String payloadJson = "{ \"rewardId\": " + (savedReward.getId() != null ? savedReward.getId() : "null") + " }";

            // Enviar notificação usando o serviço de notificação existente
            // Mas precisamos adaptar para enviar apenas para tokens específicos
            NotificationHistory notificationHistory = notificationService.sendNotificationToTokens(
                userSubscriptions.stream().map(NotificationSubscription::getToken).toList(),
                title,
                description,
                imageUrl,
                "/areacliente/recompensas",  // Deeplink para área de recompensas
                "REWARD",  // Fonte da notificação
                payloadJson  // Payload com IDs relevantes
            );

            // Atualizar a recompensa com o ID do histórico de notificação
            savedReward.setNotificationHistoryId(notificationHistory.getId());
            rewardRepository.save(savedReward);
        } else {
            log.warn("Nenhum token de notificação encontrado para o usuário sorteado: {}", selectedUserId);
        }

        return savedReward;
    }

    public List<RewardWithCustomerName> listAllRewardsWithCustomerNames() {
        return rewardRepository.findAllWithCustomerNames();
    }

    public List<RewardWithCustomerName> listRewardsByUserWithCustomerName(Long userId) {
        return rewardRepository.findByUserIdWithCustomerName(userId);
    }

    @Transactional
    public Reward createAndNotifyReward(Long userId, String title, String description, String imageUrl, LocalDateTime validUntil)
            throws FirebaseMessagingException {

        if (userId == null) {
            throw new IllegalArgumentException("userId é obrigatório para enviar brinde");
        }

        Reward reward = new Reward();
        reward.setUserId(userId);
        reward.setTitle(title);
        reward.setDescription(description);
        reward.setImageUrl(imageUrl);
        reward.setValidUntil(validUntil);
        reward.setStatus(RewardStatus.AVAILABLE);
        reward.setRedeemedAt(null);
        reward.setNotificationHistoryId(null);

        Reward savedReward = rewardRepository.save(reward);

        // Buscar tokens do usuário
        List<NotificationSubscription> userSubscriptions = subscriptionRepository.findByUserId(userId);
        if (userSubscriptions.isEmpty()) {
            log.warn("Nenhum token de notificação encontrado para userId={}", userId);
            return savedReward;
        }

        String payloadJson = "{ \"rewardId\": " + (savedReward.getId() != null ? savedReward.getId() : "null") + " }";
        NotificationHistory notificationHistory = notificationService.sendNotificationToTokens(
            userSubscriptions.stream().map(NotificationSubscription::getToken).toList(),
            title,
            description,
            imageUrl,
            "/areacliente/recompensas",
            "REWARD",
            payloadJson
        );

        savedReward.setNotificationHistoryId(notificationHistory.getId());
        rewardRepository.save(savedReward);

        return savedReward;
    }
}
