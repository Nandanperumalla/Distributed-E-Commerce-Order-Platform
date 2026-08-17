# Running this on AWS

Nothing in the code knows it is running in Docker Compose. Every dependency is
reached through an environment variable, so moving to managed services is a
configuration change rather than a rewrite.

| Compose service | AWS equivalent | What changes |
| --- | --- | --- |
| `postgres` | RDS for PostgreSQL (Multi-AZ) | `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| `redis` | ElastiCache for Redis | `REDIS_HOST`, `REDIS_PORT` |
| `kafka` | MSK (or MSK Serverless) | `KAFKA_BOOTSTRAP_SERVERS`, plus IAM/TLS client config |
| the three services | ECS Fargate services behind an internal ALB | `INVENTORY_BASE_URL` points at the ALB |

## Notes that actually matter

**Secrets.** `DB_PASSWORD` belongs in Secrets Manager, injected by the ECS task
definition as a `secrets` entry, not an `environment` one.

**Health checks.** Each service exposes `/actuator/health`. Wire it as both the
ECS container health check and the ALB target group health check, so a service
that has started but cannot reach Kafka is not sent traffic.

**Scaling.** Order intake scales on CPU or request count. The consumers scale on
Kafka consumer lag — published to CloudWatch, with the ceiling set by partition
count: three partitions means at most three useful consumers per group, so raise
partitions before raising task count.

**Migrations.** Flyway runs at startup, which is fine while one task starts
first, and a race when several start at once. Run migrations as a one-off ECS
task in the deploy pipeline and set `spring.flyway.enabled=false` on the
services.

**Networking.** Services in private subnets, ALB in public ones. RDS,
ElastiCache, and MSK reachable only from the service security group.

**Images.** Built by the same `Dockerfile` with `--build-arg MODULE=<service>`,
pushed to ECR, one repository per service.

## Example ECS task definition fragment

```json
{
  "family": "order-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "order-service",
      "image": "<account>.dkr.ecr.<region>.amazonaws.com/order-service:latest",
      "portMappings": [{ "containerPort": 8081 }],
      "environment": [
        { "name": "DB_URL", "value": "jdbc:postgresql://orders.<id>.<region>.rds.amazonaws.com:5432/orders" },
        { "name": "REDIS_HOST", "value": "orders.<id>.cache.amazonaws.com" },
        { "name": "KAFKA_BOOTSTRAP_SERVERS", "value": "b-1.<cluster>.kafka.<region>.amazonaws.com:9092" },
        { "name": "INVENTORY_BASE_URL", "value": "http://internal-alb.<region>.elb.amazonaws.com" }
      ],
      "secrets": [
        { "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:orders-db" }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health | grep -q UP"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```
