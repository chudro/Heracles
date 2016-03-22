# Heracles Software
Customer Engagement Pack 
Overview

Trinity Software is relaunching their business productivity applications (word processing, spreadsheets, presentations) as a cloud service. The service is something akin to Office 365 and Google Docs. The company wants to migrate the functionality of user authorization, account management, and real time error handling to DSE.  The application allows Clients to create new accounts, login, and work with the applications on browsers or mobile devices. If the client crashes during a session then on restart a crash report log file is sent from the client.  

The crash log is currently being written to a kafka queue and needs to be processed in real time.  The log contains the complete error string along with metadata of user id, doc type, user agent, timestamp.  The metadata needs to be parsed and stored in Cassandra along with a count of crashes per 10 second interval. Error logs are stored in raw format and need to be made text searchable.

Setup - Requirements and steps 

Data Model

Sample Inserts (CQL)


Sample Queries (CQL)


Stress YAML

Search - Setup, SOLR Schema and Sample Search Queries

Analytics - Setup and Sample Queries (batch or SparkSQL)

Data generator code


Client for loading generated data


