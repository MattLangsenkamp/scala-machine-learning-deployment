# Efficient Deployment of Machine Learning Models in Scala
This repo contains the code associated with a series of blog posts about deploying machine learning models to live services using ONNX, TensorRT, Triton, gRPC, cats-effect, fs2 and other technologies/tools. The overview of the series can be found [here](https://mattlangsenkamp.github.io/posts/scala-machine-learning-deployment-entry-0/)

## Installation and System
The code was written and tested on a Ubuntu 22.04 machine with an Nvidia RTX 3060, as well as a Ubuntu 22.04 machine with an Nvidia RTX 3080. Nvidia driver version 535.129.03, Cuda compilation tools V12.2.140 and Docker version 24.0.7. You likely do not need these exact versions of Nvidia Driver and CUDA. Try to run the code and if you have problems see the Nvidia installation instructions below.

Code was written using Scala 3.3+, sbt 1.9.7, Python 3.10+ and the [Poetry](https://python-poetry.org/) dependency manager for python. Instructions for downloading Scala, sbt, Python and Poetry can be easily found on the internet.

### Nvidia Installation
<details closed>
<summary>Expand for Nvidia installation details</summary>
In my experiance installing Nvidia tools can be tedious and error prone. Make sure to read all the documentation present in each link so you know what you are doing.

Download cuda 12.2 using this [link](https://developer.nvidia.com/cuda-12-2-2-download-archive).
Select the options in the following order `Linux > x86_64 > Ubuntu > 22.04 > deb (network)` or make modifications to meet your system requirements. For me the generated instructions look like what is below:
```bash
$ wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2204/x86_64/cuda-keyring_1.1-1_all.deb
$ sudo dpkg -i cuda-keyring_1.1-1_all.deb
$ sudo apt-get update
$ sudo apt-get -y install cuda
```
To install cuDNN you will need an Nvidia sign in. It is still free they just make you sign in.
Go to this [link](https://developer.nvidia.com/rdp/cudnn-download) to download the current version of cuDNN. If you need an older version visit this [link](https://developer.nvidia.com/rdp/cudnn-archive). Select `Local Installer for Ubuntu22.04 x86_64 (Deb)`. After that follow the instructions linked [here](https://docs.nvidia.com/deeplearning/cudnn/install-guide/index.html#installlinux-deb)

After rebooting your system run the command `nvcc -V` and you should get an outputing specifying your version. If `nvcc -V` doesnt work you might need to add `export export PATH="/usr/local/cuda/bin:$PATH"` to `~/.bashrc`

If something goes wrong and you need to start from scrath follow the instructions in this [link](https://docs.nvidia.com/cuda/cuda-installation-guide-linux/index.html#removing-cuda-toolkit-and-driver) to uninstall Nvidia stuff.
</details>

### Docker Nvidia
Finally install Docker-Nvidia using these [instructions](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

### Python Installation
Installation assumes you have the poetry build tool installed on your system. Poetry is not required to create a python environment, but if you choose not to use it you will have to modify the instructions accordingly. 

Simply run the following commands to install the dependencies specified in the `pyproject.toml` file and to activate the created environment:
```bash 
poetry install
poetry shell
```

### Scala Installation
Compile the project with the following command:
```bash
sbt compile
```
If there are no error messages then installation was successful

### Easy Docker Deployment
The easiest way to deploy the system locally is with Docker/Docker Compose.

Before you do that though you will need to configure the `docker-compose.yaml` file, by adding your client key and client secret. These are managed through github (or whatever OAuth provider you implement) and are necessary for login. 

Below is a snippet of the yaml file which shows the areas that need to be configured.
```yaml
services:
  ...
  server:
    image: mattlangsenkamp/scalamachinelearningdeployment:latest
    ports:
      - "8080:8080"
    depends_on:
      - grpctriton
    environment:
      - SERVER_HOST=0.0.0.0
      - TRITON_HOST=grpctriton
      - KEY=<add GITHUB_CLIENT_KEY here>
      - SECRET=<add GITHUB_CLIENT_SECRET>
      - LABELS_DIR=/labels.json
  ...
```

Once `docker-compose.yaml` has been configured simply run 
`docker compose up` and navigate to `localhost:5713`