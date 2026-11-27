FROM eclipse-temurin:21-jre

# 生产需要扫描版 PDF OCR 时构建参数设为 true；默认不安装以缩小镜像。
ARG INSTALL_OCR=false
RUN if [ "$INSTALL_OCR" = "true" ]; then \
      apt-get update && \
      apt-get install -y --no-install-recommends \
        tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng && \
      rm -rf /var/lib/apt/lists/*; \
    fi

WORKDIR /app
COPY target/shiye-user-api-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 18080
ENTRYPOINT ["java", "-jar", "app.jar"]
