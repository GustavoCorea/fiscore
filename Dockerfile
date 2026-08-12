# ============================================================
# Fiscore — Dockerfile (multi-stage)
# Dimensionado para el plan gratuito de Render (512 MB RAM / 0.1 CPU)
# ============================================================

# ── Etapa 1: compilación ─────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

# El wrapper y el POM primero: así la capa de dependencias solo se
# invalida cuando cambia pom.xml, no con cada cambio de código.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw

RUN ./mvnw dependency:go-offline -B -q

COPY src src
RUN ./mvnw package -DskipTests -B -q

# El build genera DOS war: el que empaqueta maven-war-plugin y el que
# Spring Boot reempaqueta con marca de tiempo (este último es el único
# ejecutable con 'java -jar'). Se elige por contenido, no por nombre,
# para no depender del formato del nombre ni del orden alfabético.
RUN set -eu; \
    for w in target/*.war; do \
      if jar tf "$w" | grep -q '^org/springframework/boot/loader/'; then \
        cp "$w" /workspace/app.war; \
        echo "WAR ejecutable: $w"; \
        break; \
      fi; \
    done; \
    test -f /workspace/app.war || { echo "ERROR: no se encontró un WAR ejecutable"; exit 1; }

# ── Etapa 2: ejecución ───────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Usuario sin privilegios
RUN addgroup -S fiscore && adduser -S fiscore -G fiscore

COPY --from=build /workspace/app.war app.war
RUN chown fiscore:fiscore app.war

USER fiscore

# Puerto por defecto; Render inyecta PORT como variable de entorno
EXPOSE 8080

# Perfil por defecto en la imagen. Render lo reafirma por envVars, pero
# fijarlo aquí garantiza que nunca arranque con configuración de desarrollo.
ENV SPRING_PROFILES_ACTIVE=prod

# Opciones de JVM para un contenedor de 512 MB y 0.1 CPU:
#   MaxRAMPercentage=55  -> ~280 MB de heap, dejando sitio a metaspace,
#                           code cache, pilas y buffers directos.
#   UseSerialGC          -> menos hilos y menos memoria que G1 con 1 vCPU.
#   MaxMetaspaceSize     -> techo duro para que el metaspace no provoque
#                           que el contenedor muera por OOM.
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=55.0 \
  -XX:+UseSerialGC \
  -XX:MaxMetaspaceSize=160m \
  -Xss512k \
  -Dspring.jmx.enabled=false \
  -Djava.security.egd=file:/dev/./urandom"

# Forma shell para que $JAVA_OPTS se expanda; 'exec' deja la JVM como PID 1
# para que reciba SIGTERM en los redespliegues de Render.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.war"]
