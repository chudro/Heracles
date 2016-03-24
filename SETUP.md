# Heracles Setup

This demo simulates a stream of movie ratings.  Data flows from akka -> kafka -> spark streaming -> cassandra

# Setting up SBT

http://www.scala-sbt.org/0.13/tutorial/Installing-sbt-on-Linux.html

## Kafka Setup

[See the Kafka Setup Instructions in the KAFKA_SETUP.md file](KAFKA_SETUP.md)


## Setup Akka Feeder

* build the feeder fat jar   
`sbt feeder/assembly`

* run the feeder

Copy the application.conf file to dev.conf and modify the zookeeper location.  Then override the configs by using -Dconfig.file=dev.conf to use the new config.

`java -Xmx1g -Dconfig.file=dev.conf -jar feeder/target/scala-2.10/feeder-assembly-1.0.jar 1 100 true 2>&1 1>feeder-out.log &`


## Run Spark Streaming

* build the streaming jar
`sbt streaming/package`

* running locally for development
  first parameter is kafka broker and the second parameter whether to display debug output  (true|false)

`dse spark-submit --packages org.apache.spark:spark-streaming-kafka-assembly_2.10:1.4.1 --class sparkAtScale.StreamingRatings streaming/target/scala-2.10/streaming_2.10-1.0.jar 172.31.18.33:9092 true`

 * running on a server in foreground
 dse spark-submit --packages org.apache.spark:spark-streaming-kafka-assembly_2.10:1.4.1 --class sparkAtScale.StreamingRatings streaming_2.10-1.0.jar localhost:9092 true

* running on the server for production mode
`nohup dse spark-submit --packages org.apache.spark:spark-streaming-kafka-assembly_2.10:1.4.1 --class sparkAtScale.StreamingRatings streaming/target/scala-2.10/streaming_2.10-1.0.jar 2>&1 1>streaming-out.log &`
