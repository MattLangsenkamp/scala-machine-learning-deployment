
poetry run python python/preprocess_models.py

# convert .onnx models into .plan models
docker run --gpus all -it -v $(pwd)/triton/:/workspace --rm nvcr.io/nvidia/tensorrt:23.10-py3 trtexec --onnx=yolov8n-cls-1.onnx --saveEngine=yolov8n-cls-1.plan --explicitBatch
docker run --gpus all -it -v $(pwd)/triton/:/workspace --rm nvcr.io/nvidia/tensorrt:23.10-py3 trtexec --onnx=yolov8n-cls-16.onnx --saveEngine=yolov8n-cls-16.plan --explicitBatch