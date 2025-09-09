# ========================
# Stage 1: Build with Maven Wrapper
# ========================
FROM eclipse-temurin:24 AS build
WORKDIR /app

# Thiết lập UTF-8 cho môi trường
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Copy pom.xml và mvnw trước để cache dependencies
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Tải dependencies (cache lại, không build code)
RUN ./mvnw dependency:go-offline -B -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8

# Copy toàn bộ source code
COPY src src

# Build jar với UTF-8
RUN ./mvnw clean package -DskipTests -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8

# ========================
# Stage 2: Runtime
# ========================
FROM eclipse-temurin:24-jre
WORKDIR /app

# Thiết lập UTF-8 cho runtime
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Copy jar từ stage 1
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-jar","app.jar"]