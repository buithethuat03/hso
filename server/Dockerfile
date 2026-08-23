# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Tải dependency trước (tận dụng layer cache khi chỉ đổi source)
COPY pom.xml .
RUN mvn -q dependency:go-offline
# Build jar (bỏ test: project không có test, tránh lỗi plugin surefire)
COPY src ./src
RUN mvn -q -DskipTests clean package

# ---------- runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Locale UTF-8 để log tiếng Việt không thành dấu hỏi (core/Log.java ghi theo default charset)
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

# Chỉ copy jar. data/, hso.conf, log/ được mount lúc chạy (xem docker-compose.yml):
#  - data/ (208MB) mount read-only, không nhồi vào image cho nhẹ và build nhanh
#  - hso.conf mount read-only để sửa cấu hình không cần build lại
#  - log/ mount read-write
COPY --from=build /build/target/HSO_Re_2-1.0-jar-with-dependencies.jar /app/server.jar
RUN mkdir -p /app/log

EXPOSE 19129

# WORKDIR /app phải chứa hso.conf + data/ (đường dẫn trong code là tương đối).
# -Xms/-Xmx đặt tường minh (run.bat không đặt, JVM tự đoán 1/4 RAM). Chỉnh theo RAM máy EC2.
# Shutdown hook đã tự lưu dữ liệu khi nhận SIGTERM (core/Start.java) nên `docker stop` là an toàn.
ENTRYPOINT ["java", "-server", \
    "-Dfile.encoding=UTF-8", \
    "-XX:+UseG1GC", \
    "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/app/log/", \
    "-Xms1g", "-Xmx3g", \
    "-jar", "/app/server.jar"]
