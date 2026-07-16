# Spring Cloud Lab

> Microservice infrastructure experiments — service discovery, configuration, gateway, resilience.

---

## Module Goals

- Understand Spring Cloud ecosystem and component interactions
- Build and operate a minimal microservice cluster
- Experiment with failure modes and resilience patterns

---

## Learning Path

| # | Experiment | Goal |
|---|-----------|------|
| SC001 | Service Discovery | Eureka/Nacos registration & discovery |
| SC002 | Config Center | Spring Cloud Config with refresh |
| SC003 | API Gateway | Spring Cloud Gateway routing & filters |
| SC004 | Circuit Breaker | Resilience4j circuit breaker & fallback |
| SC005 | Load Balancing | Spring Cloud LoadBalancer strategies |
| SC006 | OpenFeign | Declarative HTTP client with interceptors |
| SC007 | Distributed Tracing | Micrometer + Zipkin integration |

---

## Experiment Standards

- Each experiment demonstrates one Spring Cloud component
- Configuration-driven: changes should work without recompilation
- Include failure scenario testing (kill a service, observe behavior)

---

## Resources

- [Spring Cloud Reference](https://docs.spring.io/spring-cloud/docs/current/reference/html/)
- [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba)
