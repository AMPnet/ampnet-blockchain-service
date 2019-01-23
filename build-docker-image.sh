#!/bin/bash

set -o xtrace
set -e

gradle build

docker build -t blockchain-service -f etc/docker/Dockerfile .
