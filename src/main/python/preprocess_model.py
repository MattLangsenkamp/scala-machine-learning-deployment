from ultralytics import YOLO
from onnx import load
import os
import json


if __name__ == "__main__":
    # 1 This will automatically download the model from the ultralytics repo
    orig_model = YOLO("yolov8n-cls.pt")

    # 2 export model to onnx format in batch sizes 1 and 16
    orig_model.export(format="onnx")
    os.rename("yolov8n-cls.onnx", "yolov8n-cls-1.onnx")
    orig_model.export(format="onnx", batch=16)
    os.rename("yolov8n-cls.onnx", "yolov8n-cls-16.onnx")

    # 3 inspect inputs and outputs
    model = load("yolov8n-cls-1.onnx")

    print("\n\n --- inspecting model inputs and outputs --- ")
    print(" --- inputs ---")
    i = model.graph.input[0]
    print(i)
    print(" --- outputs ---")
    o = model.graph.output[0]
    print(o)
    print(" ------------------------------------------- \n\n")

    # 5 save the label lookup table
    lookup_dict = eval(model.metadata_props[-1].value)
    with open("labels.json", "w") as outfile:
        json.dump(lookup_dict, outfile)
