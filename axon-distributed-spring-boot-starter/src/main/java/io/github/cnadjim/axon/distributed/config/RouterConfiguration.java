package io.github.cnadjim.axon.distributed.config;

import io.github.cnadjim.axon.distributed.discovery.MemberRegistry;
import io.github.cnadjim.axon.distributed.router.MemberRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RouterConfiguration {

    @Primary
    @Bean("memberRouter")
    public MemberRouter memberRouter(MemberRegistry memberRegistry) {
        return new MemberRouter(memberRegistry);
    }
}
