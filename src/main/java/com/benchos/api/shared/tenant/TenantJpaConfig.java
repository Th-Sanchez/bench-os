package com.benchos.api.shared.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TenantJpaConfig {

    @Bean
    PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            ObjectProvider<TransactionManagerCustomizers> transactionManagerCustomizers
    ) {
        TenantAwareJpaTransactionManager transactionManager =
                new TenantAwareJpaTransactionManager(entityManagerFactory);
        transactionManagerCustomizers.ifAvailable(customizers -> customizers.customize(transactionManager));
        return transactionManager;
    }
}
