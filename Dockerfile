# ---------- Сборка ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Сначала только описание сборки — слой с зависимостями кэшируется между пересборками
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- Запуск ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 worktime && mkdir /data && chown worktime /data
USER worktime

COPY --from=build /app/target/worktime.jar app.jar

# Файл БД лежит в томе /data и переживает пересоздание контейнера
ENV DB_URL=jdbc:h2:file:/data/worktime
VOLUME /data
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
