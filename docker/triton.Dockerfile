FROM nvcr.io/nvidia/tritonserver:23.10-py3

RUN mkdir -p models/yolov8_1/1/
COPY ./triton/config_1.pbtxt models/yolov8_1/config.pbtxt
COPY ./triton/yolov8n-cls-1.plan models/yolov8_1/1/model.plan

RUN mkdir -p models/yolov8_16/1/
COPY ./triton/config_16.pbtxt models/yolov8_16/config.pbtxt
COPY ./triton/yolov8n-cls-16.plan models/yolov8_16/1/model.plan

CMD [ "tritonserver",  "--model-repository=/opt/tritonserver/models" ]