# Heracles Setup

This demo simulates a stream of movie ratings.  Data flows from akka -> kafka -> spark streaming -> cassandra

# Setting up Cassandra


CREATE KEYSPACE IF NOT EXISTS heracles_db WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};

create table heracles_db.error_msgs (
    error_id int primary key,
    error_msg text,
    error_time bigint
)


# Setting up SBT

http://www.scala-sbt.org/0.13/tutorial/Installing-sbt-on-Linux.html

## Be sure Java home is setup

echo $JAVA_HOME
export JAVA_HOME=/opt/jdk1.8.0_72

## Kafka Setup

[See the Kafka Setup Instructions in the KAFKA_SETUP.md file](KAFKA_SETUP.md)


## Setup Akka Feeder

* build the feeder fat jar   
`sbt feeder/assembly`

* run the feeder

Copy the application.conf file to dev.conf and modify the zookeeper location.  Then override the configs by using -Dconfig.file=dev.conf to use the new config.

`java -Xmx1g -Dconfig.file=dev.conf -jar feeder-assembly-0.1.jar 1 100 2>&1 1>feeder-out.log &`


## Run Spark Streaming

* build the streaming jar
`sbt streaming/package`

* running on a server in foreground
* first parameter is kafka broker and the second parameter whether to display debug output  (true|false)
dse spark-submit --packages org.apache.spark:spark-streaming-kafka_2.10:1.4.1 --class HeraclesStreaming.StreamingDirectRatings streaming_2.10-0.1.jar 172.31.5.154:9092 error_msgs true

* running on the server for production mode
`nohup dse spark-submit --packages org.apache.spark:spark-streaming-kafka_2.10:1.4.1 --class HeraclesStreaming.StreamingDirectRatings streaming_2.10-0.1.jar 172.31.5.154:9092 error_msgs true 2>&1 1>streaming-out.log &`
