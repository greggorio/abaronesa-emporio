package com.baronesa.emporio;

import com.baronesa.emporio.entity.JobDefinition;
import com.baronesa.emporio.repository.JobDefinitionRepository;
import com.baronesa.emporio.service.JobSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.job-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class JobDefinitionSeeder implements CommandLineRunner {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobSchedulerService jobSchedulerService;

    @Override
    @Transactional
    public void run(String... args) {
        seedJob("BIRTHDAY_PRE", "Aniversário - Pré", "0 0 9 * * *",
                "Dispara notificações de pré-aniversário na janela configurada (site_aniversario_dias_antes).");
        seedJob("BIRTHDAY_DAY", "Aniversário - Dia", "0 5 9 * * *",
                "Dispara notificações de aniversário para clientes que fazem aniversário hoje.");
        seedJob("EVENT_PRE", "Evento - Pré", "0 10 9 * * *",
                "Dispara notificações de pré-evento na janela configurada (site_evento_dias_antes).");
        seedJob("EVENT_DAY", "Evento - Dia", "0 15 9 * * *",
                "Dispara notificações no dia do evento.");
        seedJob("TRANSLATION_SYNC", "Traduções — Cardápio", "0 0 * * * *",
                "Processa traduções pendentes do cardápio (entity_translation).");
        seedJob("THEME_TRANSLATION_SEED", "Traduções — Tema Espresso", "0 30 1 * * *",
                "Gera/atualiza entradas PENDING do tema ativo do espresso para os locales alvo.");
        seedJob("VOUCHER_EXCEDENTE", "Voucher — Excedente mês anterior", "0 0 0 1 * *",
                "Gera contas a receber para excedente de voucher do mês anterior.");
        seedJob("PRODUCT_SIGNAGE", "Signage — Produtos", "0 0/30 * * * *",
                "Detecta produtos habilitados para signage com dados completos.");
        seedJob("SIGNAGE_VIDEO_CLEANUP", "Signage — Limpeza de Vídeos", "0 30 4 * * *",
                "Remove MP4 antigos do signage no disco (mantém os N últimos por produto).");
    }

    private void seedJob(String key, String name, String cron, String description) {
        Optional<JobDefinition> existing = jobDefinitionRepository.findByKey(key);
        if (existing.isPresent()) {
            return;
        }
        JobDefinition job = JobDefinition.builder()
                .key(key)
                .name(name)
                .cron(cron)
                .active(true)
                .description(description)
                .build();
        jobSchedulerService.create(job);
        log.info("Seed job_definition criado: {} ({})", key, cron);
    }
}
