FROM mattlangsenkamp/scalamachinelearningdeployment-mid:latest

ENV JAVA_OPTS="-Dotel.java.global-autoconfigure.enabled=true -Dotel.service.name=scala-machine-learning -Dotel.exporter.otlp.endpoint=http://otel-collector:4317"

COPY labels.json labels.json