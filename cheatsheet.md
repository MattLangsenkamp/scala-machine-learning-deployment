### run just triton
docker run --gpus all -p 8000:8000 -p 8001:8001 -p 8002:8002 mattlangsenkamp/tritondeployment:latest

### run just triton CPU
docker run -p 8000:8000 -p 8001:8001 -p 8002:8002 mattlangsenkamp/tritondeployment:cpu

### run just the server
docker run -p 8080:8080 -e KEY=foo -e SECRET=bar -e LABELS_DIR=/labels.json -e TRITON_HOST=127.0.0.1 -e SERVER_HOST=0.0.0.0 mattlangsenkamp/scalamachinelearningdeployment

### run just the client
docker run -p 5173:5173 mattlangsenkamp/classificationclient:dev

### send a request
curl -v -F file=@./images/ambulance.jpeg -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE4NjEwNjYzNDAsImlhdCI6MTcwMzI4MTU4MCwKICAiZW1haWwiIDogIm1hdHRsYW5nc2Vua2FtcEB5YWhvby5jb20iCn0.5Qz1FGBjVfQ_OfpTH4g7DXh7o8yv-YyGMssJYgYZ0mk"  "http://localhost:8080/infer/infer?model=yolov8_1&top_k=10&batch_size=1"

### get into a running container to debug
docker exec -it scalamachinelearningdeployment-server-1 bash 

docker compose -f docker-compose.yaml down

