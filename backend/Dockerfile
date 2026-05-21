FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

ENV PRODUCT_IMAGE_UPLOAD_DIR=/app/uploads/product-images

RUN useradd --system --create-home --uid 10001 appuser \
    && mkdir -p /app/uploads/product-images \
    && chown -R appuser:appuser /app

COPY --from=build --chown=appuser:appuser /workspace/target/backend-*.jar /app/app.jar

USER appuser

EXPOSE 8080
VOLUME ["/app/uploads/product-images"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
