FROM nvcr.io/nvidia/tritonserver:23.10-py3

RUN mkdir -p models/yolov8_16_batch/1/
COPY ./triton/config_16_batch_cpu.pbtxt models/yolov8_16_batch/config.pbtxt
COPY ./triton/yolov8n-cls-16.onnx models/yolov8_16_batch/1/model.onnx

CMD [ "tritonserver",  "--model-repository=/opt/tritonserver/models" ]