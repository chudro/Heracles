# Heracles Software
Customer Engagement Pack 
Overview

Trinity Software is relaunching their business productivity applications (word processing, spreadsheets, presentations) as a cloud service. The service is something akin to Office 365 and Google Docs. The company wants to migrate the functionality of user authorization, account management, and real time error handling to DSE.  The application allows Clients to create new accounts, login, and work with the applications on browsers or mobile devices. If the client crashes during a session then on restart a crash report log file is sent from the client.  

The crash log is currently being written to a kafka queue and needs to be processed in real time.  The log contains the complete error string along with metadata of user id, doc type, user agent, timestamp.  The metadata needs to be parsed and stored in Cassandra along with a count of crashes per 10 second interval. Error logs are stored in raw format and need to be made text searchable.

# Reasons for DataStax Enterprise

## Error Log Fuzzy and Faceted Search with DSE

- DataStax Enterprise extends Cassandra with rich Search integration
- Indexing of Error Logs provides fuzzy and faceted Search capabilities
- No need to ETL data to external Search tool
- Provides live-endexing of data on ingest
- Bucketing of log data enables search by various time series grains

## Creating new user account guaranteed to be unique

Cassandra Lightweight Transactions
Also known as Compare-And-Set, Lightweight Transactions are Cassandra’s way to ensure data is not automatically overwritten (upserted) per its the default behavior
Such a transaction uses serial consistency which causes it to be inserted in the current flow of data modifications, committing if necessary a transaction in process
It is achieved by using the syntax:
“insert … if not exists” 
or
“update … if value = ‘some value’ ”

# Setup - Requirements and steps 

## Setting up Cassandra


CREATE KEYSPACE IF NOT EXISTS heracles_db WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};

create table heracles_db.error_msgs (
    error_id int primary key,
    error_msg text,
    error_time bigint
)


## Setting up SBT

http://www.scala-sbt.org/0.13/tutorial/Installing-sbt-on-Linux.html

### Be sure Java home is setup

echo $JAVA_HOME
export JAVA_HOME=/opt/jdk1.8.0_72

### Kafka Setup

[See the Kafka Setup Instructions in the KAFKA_SETUP.md file](KAFKA_SETUP.md)


### Setup Akka Feeder

* build the feeder fat jar   
`sbt feeder/assembly`

* run the feeder

Copy the application.conf file to dev.conf and modify the zookeeper location.  Then override the configs by using -Dconfig.file=dev.conf to use the new config.

`java -Xmx1g -Dconfig.file=dev.conf -jar feeder-assembly-0.1.jar 1 100 2>&1 1>feeder-out.log &`


### Run Spark Streaming

* build the streaming jar
`sbt streaming/package`

* running on a server in foreground
* first parameter is kafka broker and the second parameter whether to display debug output  (true|false)
dse spark-submit --packages org.apache.spark:spark-streaming-kafka_2.10:1.4.1 --class HeraclesStreaming.StreamingDirectRatings streaming_2.10-0.1.jar 172.31.5.154:9092 error_msgs true

* running on the server for production mode
`nohup dse spark-submit --packages org.apache.spark:spark-streaming-kafka_2.10:1.4.1 --class HeraclesStreaming.StreamingDirectRatings streaming_2.10-0.1.jar 172.31.5.154:9092 error_msgs true 2>&1 1>streaming-out.log &`

# Data Model

```
CREATE KEYSPACE IF NOT EXISTS heracles_db WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};
```

```
CREATE TABLE heracles_db.error_msgs (
    error_id int primary key,
    error_msg text,
    error_time bigint
);
```

```
CREATE TABLE heracles.error_log (
    userid text,
    createdtime timestamp,
    doctype text,
    errorcode text,
    errorstring text,
    useragent text,
    PRIMARY KEY (userid, createdtime)
) WITH CLUSTERING ORDER BY (createdtime DESC);
```

```
CREATE TABLE heracles.crash_count (
    useragent text,
    doctype text,
    bucket bigint,
    crashcount int,
    PRIMARY KEY ((useragent, doctype, bucket))
);
```

```
CREATE TABLE heracles.login_count (
    userid text,
    bucket bigint,
    logincount int,
    PRIMARY KEY ((userid, bucket))
); 
```

# Sample Inserts (CQL)

```
use heracles;
insert into heracles.error_log (userid, doctype, useragent, createdtime, errorcode, errorstring) values ('123', 'Spreadsheet', 'Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36', '2016-01-01 13:00:00-0100', 'Crash', 'Invalid Operation caused crash');
insert into heracles.error_log (userid, doctype, useragent, createdtime, errorcode, errorstring) values ('123', 'Word', 'Safari/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36', '2016-01-01 14:00:00-0100', 'Crash', 'Invalid Operation caused crash');
insert into heracles.error_log (userid, doctype, useragent, createdtime, errorcode, errorstring) values ('123', 'Powerpoint', 'Chrome/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36', '2016-01-01 15:00:00-0100', 'Crash', 'Invalid Operation caused crash');
insert into heracles.error_log (userid, doctype, useragent, createdtime, errorcode, errorstring) values ('456', 'Word', 'Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36', '2016-01-01 16:00:00-0100', 'Crash', 'Invalid Operation caused crash');
insert into heracles.error_log (userid, doctype, useragent, createdtime, errorcode, errorstring) values ('456', 'Powerpoint', 'Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36', '2016-01-01 17:00:00-0100', 'Crash', 'Invalid Operation caused crash');
insert into heracles.error_log (userid, doctype, useragent, createdtime, errorcode, errorstring) values ('456', 'Spreadsheet', 'Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36', '2016-01-01 18:00:00-0100', 'Crash', 'Invalid Operation caused crash');
```

# Sample Queries (CQL)

```
select * from heracles.error_log where userid = 123 and createdtime > '2016-01-01 13:00:00-0100' and createdtime < '2016-01-01 15:00:00-0100';
```
# Stress YAML

# Search - Setup, SOLR Schema and Sample Search Queries

Below is the SOLR setup for querying the error logs.

## Create Core

```
dsetool create_core heracles.error_log schema=~/github/heracles/solr/schema.xml solrconfig=~/github/heracles/solr/solrconfig.xml
```

## Reload Core:

```
dsetool reload_core heracles.error_log reindex=true schema=~/github/heracles/solr/schema.xml solrconfig=~/github/heracles/solr/solrconfig.xml
```

# Analytics - Setup and Sample Queries (batch or SparkSQL)

# Data generator code


# Client for loading generated data


