for PROTO in 'grpc_service' 'health' 'model_config'
do
    wget -O ./protobuf/src/main/protobuf/$PROTO.proto https://raw.githubusercontent.com/triton-inference-server/common/main/protobuf/$PROTO.proto
done