#!/bin/bash
docker rm -f `docker ps -a | grep -v "CONTAINER ID" | awk '{print $1}'`
