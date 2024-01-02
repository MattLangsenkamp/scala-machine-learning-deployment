env JAVA_OPTS="-Xmx2048m" sbt clean "server/assembly"
docker buildx build -f server.Dockerfile -t mattlangsenkamp/scalamachinelearningdeployment:latest .
docker buildx build -f Dockerfile -t mattlangsenkamp/tritondeployment .

sbt "front/fullLinkJS"
npm run build
docker buildx build -f client.Dockerfile -t mattlangsenkamp/classificationclient:dev .

# hiding the production env file so that the client will default to using mldemo.mattlangsenkamp.com/callback as the callback
mv .env.production hold
npm run build
docker buildx build -f client.Dockerfile -t mattlangsenkamp/classificationclient:latest .
mv hold .env.production