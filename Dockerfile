# ========================
# Stage 1: Build with Maven Wrapper
# ========================
FROM eclipse-temurin:24 AS build
WORKDIR /app

# Copy pom.xml và mvnw trước để cache dependencies
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Tải dependencies (cache lại, không build code)
RUN ./mvnw dependency:go-offline -B

# Copy toàn bộ source code
COPY src src

# Build jar
RUN ./mvnw clean package -DskipTests

# ========================
# Stage 2: Runtime
# ========================
FROM eclipse-temurin:24-jre
WORKDIR /app

# Copy jar từ stage 1
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]