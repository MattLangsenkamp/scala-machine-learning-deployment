# build backend container
env JAVA_OPTS="-Xmx2048m" sbt clean "server/docker"
docker buildx build -f docker/server.Dockerfile -t mattlangsenkamp/scalamachinelearningdeployment:latest .
docker buildx build -f docker/triton.Dockerfile -t mattlangsenkamp/tritondeployment .

# client containers
sbt "client/fullLinkJS"
npm run build
docker buildx build -f docker/client.Dockerfile -t mattlangsenkamp/classificationclient:dev .

# hiding the production env file so that the client will default to using mldemo.mattlangsenkamp.com/callback as the callback
mv .env.production hold
npm run build
docker buildx build -f docker/client.Dockerfile -t mattlangsenkamp/classificationclient:latest .
mv hold .env.production

# triton
docker buildx build -f docker/triton.Dockerfile . -t mattlangsenkamp/tritondeployment:latest
docker buildx build -f docker/triton_cpu.Dockerfile . -t mattlangsenkamp/tritondeployment:cpu