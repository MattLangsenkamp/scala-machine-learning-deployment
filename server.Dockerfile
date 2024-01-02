FROM mattlangsenkamp/scalamachinelearningdeployment-mid:latest
# FROM eclipse-temurin:21-jre

#EXPOSE 8080
#EXPOSE 8000
#EXPOSE 8001

COPY labels.json labels.json
# COPY server/target/scala-3.3.1/fat.jar /app/fat.jar

# ENTRYPOINT ["java", "-cp", "\/app\/fat.jar", "-Dcats.effect.stackTracingMode=full", "com.mattlangsenkamp.server.Server"]