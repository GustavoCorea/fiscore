# ── Stage 1: Build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Deploy ─────────────────────────────────────────────
FROM tomcat:10.1-jdk17-corretto

# Limpiar webapps por defecto
RUN rm -rf /usr/local/tomcat/webapps/*

# Copiar WAR → context path /fiscore
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/fiscore.war

# Render inyecta $PORT dinámicamente; ajustamos Tomcat antes de arrancar
CMD ["/bin/sh", "-c", \
  "sed -i 's/port=\"8080\"/port=\"'${PORT:-8080}'\"/' /usr/local/tomcat/conf/server.xml && \
   catalina.sh run"]