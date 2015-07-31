#!/bin/bash
docker stop `docker ps -a | grep -v "CONTAINER ID" | awk '{print $1}'`
