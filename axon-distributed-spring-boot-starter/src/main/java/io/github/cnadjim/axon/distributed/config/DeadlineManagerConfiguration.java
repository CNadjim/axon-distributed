package com.axon.distributed.config;


import com.axon.distributed.deadline.CombinedDeadlineManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.deadline.dbscheduler.DbSchedulerDeadlineManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeadlineManagerConfiguration {

    /**
     * Crée un CombinedDeadlineManager (transient + persistant) quand db-scheduler est disponible.
     * Les deadlines courtes (< 5 min) utilisent SimpleDeadlineManager (in-memory),
     * les deadlines longues utilisent DbSchedulerDeadlineManager (base de données).
     */
    @Bean
    @ConditionalOnBean(DbSchedulerDeadlineManager.class)
    public DeadlineManager deadlineManager(
            DbSchedulerDeadlineManager dbSchedulerDeadlineManager,
            org.axonframework.config.Configuration configuration,
            TransactionManager transactionManager
    ) {
        return new CombinedDeadlineManager(
                new SimpleDeadlineManager.Builder()
                        .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                        .transactionManager(transactionManager)
                        .build(),
                dbSchedulerDeadlineManager
        );
    }

    /**
     * Fallback : SimpleDeadlineManager seul quand db-scheduler n'est pas sur le classpath.
     * Uniquement pour les deadlines in-memory (non persistantes au redémarrage).
     */
    @Bean
    @ConditionalOnMissingBean(DbSchedulerDeadlineManager.class)
    public DeadlineManager simpleDeadlineManager(
            org.axonframework.config.Configuration configuration,
            TransactionManager transactionManager
    ) {
        return new SimpleDeadlineManager.Builder()
                .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                .transactionManager(transactionManager)
                .build();
    }
}
