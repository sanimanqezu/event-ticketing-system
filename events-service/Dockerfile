# ---------- Build stage ----------
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Install Maven and build the JAR (skip tests — run separately in CI)
RUN apk add --no-cache maven \
 && mvn -B package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
