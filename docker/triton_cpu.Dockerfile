FROM nvcr.io/nvidia/tritonserver:23.10-py3

RUN mkdir -p models/yolov8_1/1/
COPY ./triton/config_1_cpu.pbtxt models/yolov8_1/config.pbtxt
COPY ./triton/yolov8n-cls-1.onnx models/yolov8_1/1/model.onnx

RUN mkdir -p models/yolov8_16/1/
COPY ./triton/config_16_cpu.pbtxt models/yolov8_16/config.pbtxt
COPY ./triton/yolov8n-cls-16.onnx models/yolov8_16/1/model.onnx

CMD [ "tritonserver",  "--model-repository=/opt/tritonserver/models" ]