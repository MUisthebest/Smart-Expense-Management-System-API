package com.example.smartmoney.infrastructure.common;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Lettuce ClientResources for both the core and AI Redis connections.
 *
 * netty-resolver-dns is on the classpath, which makes Lettuce default to
 * Netty's own async DNS resolver instead of the JVM's. On networks where the
 * system nameserver is IPv6-only (or otherwise unreachable via Netty's UDP
 * resolver), that fails with UnsupportedAddressTypeException and every Redis
 * command errors with "Unable to connect to Redis" even though the host is
 * reachable. Forcing DnsResolvers.JVM_DEFAULT makes Lettuce resolve hostnames
 * the same way the rest of the JVM does (InetAddress.getAllByName), avoiding
 * that failure mode.
 */
@Configuration
public class RedisClientResourcesConfig {

    @Bean(destroyMethod = "shutdown")
    public ClientResources redisClientResources() {
        return DefaultClientResources.builder()
                .dnsResolver(DnsResolvers.JVM_DEFAULT)
                .build();
    }
}
