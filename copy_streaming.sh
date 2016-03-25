#!/bin/bash

nodes='52.87.167.87 52.90.61.67 54.88.200.155 52.90.53.128'

for node in $nodes
do
     scp -i /apps/bo_rightscale_ssh streaming/target/scala-2.10/streaming_2.10-0.1.jar root@$node:/root
done
