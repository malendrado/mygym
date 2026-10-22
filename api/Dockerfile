# API de mygym (Spring Boot 3.5 / Java 21) como imagen portable: sirve en Fly.io, Render, Cloud Run, un VPS, etc.
# Railway no la necesita (compila solo); existe para poder mudar la API sin depender de un proveedor.
# Construir:  docker build -t mygym-api api/
# Ejecutar:   docker run --rm -p 8080:8080 --env-file <archivo-con-las-variables> mygym-api
# Las variables de entorno obligatorias están en docs/operaciones/portabilidad-infraestructura.md (sección 2).

# ---- 1) Compilación ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Primero solo el pom: mientras no cambien las dependencias, esta capa se reutiliza.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Sin tests: necesitan la base de datos real (SUPABASE_DB_URL) y no corresponden a la construcción de la imagen.
RUN mvn -q -B -Dmaven.test.skip=true package

# ---- 2) Ejecución ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home mygym
COPY --from=build /build/target/*.jar /app/app.jar
USER mygym
# El proveedor suele fijar PORT; la app lo lee (server.port: ${PORT:8080}).
EXPOSE 8080
# El 75 % de la memoria del contenedor para el heap; si se queda sin memoria, que el proceso termine y la plataforma lo reinicie.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
