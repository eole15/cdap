.. meta::
    :author: Cask Data, Inc.
    :description: Introduction to the Cask Data Application Platform
    :copyright: Copyright © 2015 Cask Data, Inc.


.. _introduction-to-cdap:

==================================================
Introduction to CDAP
==================================================

Simple Access to Powerful Technology
====================================

The idea of CDAP is captured in our phrase, *Simple Access to Powerful Technology*. Our
goal is to provide access to the powerful technology of Apache Hadoop |(R)| through a
unified big data platform for both the cloud and on-premises.

In this introduction to CDAP, we're going to show how CDAP provides that access,
demonstrated through a comparison between using the current technologies available from
the Hadoop ecosystem and using CDAP |---| a new paradigm.

For each example action, we describe a current approach, the steps involved and the
technologies required, and then show an equivalent CDAP command with the resulting output
from the CDAP Command Line Interface. (Output has been reformatted to fit the webpage
as required.)

To try this yourself, :ref:`download a copy of CDAP SDK <standalone-index>`, install it
and then use the resources in its ``examples`` directory as you follow along.

We'll look at these areas:
  - `Data Ingestion`_
  - `Data Exploration`_
  - `Data Exploration: Attaching A Schema`_
  - `Advanced Data Exploration`_
  - `Transforming Your Data`_
  - `Building Real World Applications`_

.. highlight:: console

Installation
============
- Download and install CDAP to run in standalone mode on a laptop
- Startup CDAP
- Startup CDAP Command Line Interface


.. container:: table-block

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Install and start Servers
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Install and startup **Hadoop** and related technologies, as required
         
     * - Using CDAP
       - - Install CDAP by downloading zipfile, unzipping and starting **CDAP Server**
      
     * -  
       - | ``$ unzip cdap-sdk-``\ |literal-release|\ ``.zip``
         | ``$ cd cdap-sdk-``\ |literal-release|
         | ``$ ./bin/cdap.sh start``
         
         ::

          Starting Standalone CDAP ................
          Standalone CDAP started successfully.
          Connect to the Console at http://localhost:9999


.. container:: table-block

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Start command line interface
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1
     
     * - Without CDAP
       - - Run Hive commands using **Hive CLI** (HiveServer and Beeline)
       
     * - Using CDAP
       - - Start **CDAP CLI** (Command Line Interface)

     * - 
       - ``$ ./bin/cdap-cli.sh``
         ::

          Successfully connected CDAP instance at http://localhost:10000
          cdap (http://localhost:10000/default)> 

Data Ingestion
==============
- Data is ingested into CDAP using :ref:`Streams <streams>`
- Streams are abstractions over HDFS with an HTTP endpoint
- Data in a Stream are ordered and time-partitioned
- CDAP supports easy exploration and processing in both realtime and batch
- Ingest using RESTful, Flume, language-specific APIs, or Tools
- The abstraction of Streams lets you disconnect how you ingest from how you process


.. container:: table-block

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Create a Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Create a Time partitioned file in **HDFS**
         - Configure **Kafka** or **Flume** to write to time partitions
         
     * - Using CDAP
       - ``> create stream logEventStream``
          
     * -  
       - ::
       
          Successfully created stream with ID 'logEventStream'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1

     * - Send data to the Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Write a custom consumer for Kafka that reads from source
         - Write the data to HDFS
         - Create external table in **Hive** called ``stream_logeventstream``
         
     * - Using CDAP
       - ``> load stream logEventStream examples/resources/accesslog.txt``
          
     * -  
       - ::
       
          Successfully sent stream event to stream 'logEventStream'


Data Exploration
================
- Immediately start with the exploration of your ingested data
- Introspect raw data or view data within a time range
- Easily inspect the quality of data by generating data stats
- Easily associate a schema once you know your data: "schema on read"
- Support different data formats; extensible to support custom formats
- Supported data formats include Avro, Text, CSV, TSV, CLF, and Custom
- Query using SQL


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Describe ingested Data
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive command using Hive CLI       
         - ``DESCRIBE stream_logeventstream``
         
     * - Using CDAP
       - ``> execute 'describe stream_logEventStream'``
          
     * -  
       - ::

          +=========================================================================================================+
          | col_name: STRING                 | data_type: STRING                | comment: STRING                   |
          +=========================================================================================================+
          | ts                               | bigint                           | from deserializer                 |
          | headers                          | map<string,string>               | from deserializer                 |
          | body                             | string                           | from deserializer                 |
          +=========================================================================================================+


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve first two events from the Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive command using Hive CLI
         - ``SELECT * FROM stream_logeventstream LIMIT 2``

     * - Using CDAP
       - ``> execute 'select * from stream_logEventStream limit 2'``
          
     * -  
       - ::

          +==============================================================================================================+
          | stream_logeventstream.ts: | stream_logeventstream.hea | stream_logeventstream.body: STRING                   |
          | BIGINT                    | ders: map<string,string>  |                                                      |
          +==============================================================================================================+
          | 1428969220987             | {"content.type":"text/pla | 69.181.160.120 - - [08/Feb/2015:04:36:40 +0000] "GET |
          |                           | in"}                      |  /ajax/planStatusHistoryNeighbouringSummaries.action |
          |                           |                           | ?planKey=COOP-DBT&buildNumber=284&_=1423341312519 HT |
          |                           |                           | TP/1.1" 200 508 "http://builds.cask.co/browse/COOP-D |
          |                           |                           | BT-284/log" "Mozilla/5.0 (Macintosh; Intel Mac OS X  |
          |                           |                           | 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chro |
          |                           |                           | me/38.0.2125.122 Safari/537.36"                      |
          |--------------------------------------------------------------------------------------------------------------|
          | 1428969220987             | {"content.type":"text/pla | 69.181.160.120 - - [08/Feb/2015:04:36:47 +0000] "GET |
          |                           | in"}                      |  /rest/api/latest/server?_=1423341312520 HTTP/1.1" 2 |
          |                           |                           | 00 45 "http://builds.cask.co/browse/COOP-DBT-284/log |
          |                           |                           | " "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) A |
          |                           |                           | ppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.21 |
          |                           |                           | 25.122 Safari/537.36"                                |
          +==============================================================================================================+
          Fetched 2 rows


Data Exploration: Attaching A Schema
====================================

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Apply a *Combined log format* schema to data in the Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Drop the external Hive table
         - Recreate the Hive table with new schema
         
     * - Using CDAP
       - ``> set stream format logEventStream clf``
          
     * -  
       - ::

          Successfully set format of stream 'logEventStream'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Describe new format of the ingested Data
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive command using Hive CLI
         - ``DESCRIBE stream_logeventsetream``
         
     * - Using CDAP
       - ``> execute 'describe stream_logEventStream'``
          
     * -  
       - ::

          +=============================================================================+
          | col_name: STRING          | data_type: STRING       | comment: STRING       |
          +=============================================================================+
          | ts                        | bigint                  | from deserializer     |
          | headers                   | map<string,string>      | from deserializer     |
          | remote_host               | string                  | from deserializer     |
          | remote_login              | string                  | from deserializer     |
          | auth_user                 | string                  | from deserializer     |
          | date                      | string                  | from deserializer     |
          | request                   | string                  | from deserializer     |
          | status                    | int                     | from deserializer     |
          | content_length            | int                     | from deserializer     |
          | referrer                  | string                  | from deserializer     |
          | user_agent                | string                  | from deserializer     |
          +=============================================================================+
          Fetched 11 rows

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve first two events from the Stream, in new format
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive command using Hive CLI
         - ``SELECT * FROM stream_logeventsetream LIMIT 2``
         
     * - Using CDAP
       - ``> execute 'select * from stream_logEventStream limit 2'``
          
     * -  
       - ::

          +===================================================================================================================+
          | stream_ | stream_ | stream_ | stream_ | stream_ | stream_ | stream_ | stream_ | stream_ | stream_ | stream_logeve |
          | logeven | logeven | logeven | logeven | logeven | logeven | logeven | logeven | logeven | logeven | ntstream.user |
          | tstream | tstream | tstream | tstream | tstream | tstream | tstream | tstream | tstream | tstream | _agent: STRIN |
          | .ts: BI | .header | .remote | .remote | .auth_u | .date:  | .reques | .status | .conten | .referr | G             |
          | GINT    | s: map< | _host:  | _login: | ser: ST | STRING  | t: STRI | : INT   | t_lengt | er: STR |               |
          |         | string, | STRING  |  STRING | RING    |         | NG      |         | h: INT  | ING     |               |
          |         | string> |         |         |         |         |         |         |         |         |               |
          +===================================================================================================================+
          | 1428100 | {}      | 255.255 |         |         | 23/Sep/ | GET /cd | 401     | 2969    |         | Mozilla/4.0 ( |
          | 343436  |         | .255.18 |         |         | 2014:11 | ap.html |         |         |         | compatible; M |
          |         |         | 5       |         |         | :45:38  |  HTTP/1 |         |         |         | SIE 7.0; Wind |
          |         |         |         |         |         | -0400   | .0      |         |         |         | ows NT 5.1)   |
          |-------------------------------------------------------------------------------------------------------------------|
          | 1428100 | {}      | 255.255 |         |         | 23/Sep/ | GET /cd | 401     | 2969    |         | Mozilla/4.0 ( |
          | 483106  |         | .255.18 |         |         | 2014:11 | ap.html |         |         |         | compatible; M |
          |         |         | 5       |         |         | :45:38  |  HTTP/1 |         |         |         | SIE 7.0; Wind |
          |         |         |         |         |         | -0400   | .0      |         |         |         | ows NT 5.1)   |
          +===================================================================================================================+

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve basic Stream statistics
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - Write code to compute the various stats: number of unique elements, histograms, etc.
         
     * - Using CDAP
       - ``> get stream-stats logEventStream limit 1000``
          
     * -  
       - ::

          column: stream_logeventstream.remote_host, type: STRING
          Unique elements: 6

          column: stream_logeventstream.remote_login, type: STRING
          Unique elements: 0

          column: stream_logeventstream.auth_user, type: STRING
          Unique elements: 0

          column: stream_logeventstream.date, type: STRING
          Unique elements: 750

          column: stream_logeventstream.request, type: STRING
          Unique elements: 972

          column: stream_logeventstream.status, type: INT
          Unique elements: 4
          Histogram:
            [200, 299]: 977  |+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            [300, 399]: 17   |
            [400, 499]: 6    |

          column: stream_logeventstream.content_length, type: INT
          Unique elements: 142
          Histogram:
            [0, 99]: 205           |+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            [100, 199]: 1          |
            [200, 299]: 9          |+
            [300, 399]: 9          |+
            [400, 499]: 3          |
            [500, 599]: 300        |+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            [600, 699]: 4          |
            [800, 899]: 2          |
            [900, 999]: 1          |
            [1300, 1399]: 10       |++
            [1400, 1499]: 206      |++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            [1500, 1599]: 2        |
            [1600, 1699]: 2        |
            [2500, 2599]: 1        |
            [2700, 2799]: 1        |
            [2800, 2899]: 1        |
            [4200, 4299]: 1        |
            [5700, 5799]: 5        |
            [7100, 7199]: 1        |
            [7300, 7399]: 4        |
            [7800, 7899]: 1        |
            [8200, 8299]: 5        |
            [8700, 8799]: 3        |
            [8800, 8899]: 12       |++
            [8900, 8999]: 22       |+++++
            [9000, 9099]: 16       |+++
            [9100, 9199]: 9        |+
            [9200, 9299]: 4        |
            [9300, 9399]: 3        |
            [9400, 9499]: 5        |
            [9600, 9699]: 1        |
            [9700, 9799]: 2        |
            [9800, 9899]: 39       |++++++++++
            [9900, 9999]: 4        |
            [10000, 10099]: 1      |
            [10100, 10199]: 8      |+
            [10200, 10299]: 1      |
            [10300, 10399]: 3      |
            [10400, 10499]: 1      |
            [10500, 10599]: 1      |
            [10600, 10699]: 9      |+
            [10700, 10799]: 32     |++++++++
            [10800, 10899]: 5      |
            [10900, 10999]: 3      |
            [11000, 11099]: 4      |
            [11100, 11199]: 1      |
            [11200, 11299]: 4      |
            [11300, 11399]: 2      |
            [11500, 11599]: 1      |
            [11800, 11899]: 3      |
            [17900, 17999]: 2      |
            [36500, 36599]: 1      |
            [105800, 105899]: 1    |
            [397900, 397999]: 2    |
            [1343400, 1343499]: 1  |
            [1351600, 1351699]: 1  |

          column: stream_logeventstream.referrer, type: STRING
          Unique elements: 8

          column: stream_logeventstream.user_agent, type: STRING
          Unique elements: 4

          Analyzing 1000 Stream events in the time range [0, 9223372036854775807]...


Advanced Data Exploration
=========================
- CDAP has the ability to join multiple Streams using SQL
- Data in a Stream can be ingested in realtime or batch
- CDAP supports joining with other Streams using Hive SQL


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Create an additional Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Create a file in Hadoop file system called ``ip2geo``
         
     * - Using CDAP
       - ``> create stream ip2geo``
          
     * -  
       - ::

          Successfully created stream with ID 'ip2geo'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Ingest CSV-formatted "IP-to-geo location" data into Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Write a custom consumer that reads from source (example: Kafka)
         - Write the data to HDFS
         - Create external table in Hive called ``stream_ip2geo``

     * - Using CDAP
       - ``> load stream ip2geo examples/resources/ip2geo-maps.csv``
          
     * -  
       - ::

          Successfully sent stream event to stream 'ip2geo'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Send individual event to Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - Write data to Kafka or append directly to HDFS
         
     * - Using CDAP
       - ``> send stream ip2geo '69.181.160.120, Los Angeles, CA'``
          
     * -  
       - ::

          Successfully sent stream event to stream 'ip2geo'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve events from the Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive command using Hive CLI
         - ``SELECT * FROM stream_ip2geo``
         
     * - Using CDAP
       - ``> execute 'select * from stream_ip2geo'``
          
     * -  
       - ::

          +===========================================================================================================+
          | stream_ip2geo.ts: BIGINT | stream_ip2geo.headers: map<string,string> | stream_ip2geo.body: STRING         |
          +===========================================================================================================+
          | 1428892912060            | {"content.type":"text/csv"}               | 108.206.32.124, Santa Clara, CA    |
          | 1428892912060            | {"content.type":"text/csv"}               | 109.63.206.34, San Jose, CA        |
          | 1428892912060            | {"content.type":"text/csv"}               | 113.72.144.115, New York, New York |
          | 1428892912060            | {"content.type":"text/csv"}               | 123.125.71.19, Palo Alto, CA       |
          | 1428892912060            | {"content.type":"text/csv"}               | 123.125.71.27, Redwood, CA         |
          | 1428892912060            | {"content.type":"text/csv"}               | 123.125.71.28, Los Altos, CA       |
          | 1428892912060            | {"content.type":"text/csv"}               | 123.125.71.58, Mountain View, CA   |
          | 1428892912060            | {"content.type":"text/csv"}               | 142.54.173.19, Houston, TX         |
          | 1428892912060            | {"content.type":"text/csv"}               | 144.76.137.226, Dallas, TX         |
          | 1428892912060            | {"content.type":"text/csv"}               | 144.76.201.175, Bedminister, NJ    |
          | 1428892912060            | {"content.type":"text/csv"}               | 162.210.196.97, Milipitas, CA      |
          | 1428892912060            | {"content.type":"text/csv"}               | 188.138.17.205, Santa Barbara, CA  |
          | 1428892912060            | {"content.type":"text/csv"}               | 195.110.40.7, Orlando, FL          |
          | 1428892912060            | {"content.type":"text/csv"}               | 201.91.5.170, Tampa, FL            |
          | 1428892912060            | {"content.type":"text/csv"}               | 220.181.108.158, Miami, FL         |
          | 1428892912060            | {"content.type":"text/csv"}               | 220.181.108.161, Chicago, IL       |
          | 1428892912060            | {"content.type":"text/csv"}               | 220.181.108.184, Philadelphia, PA  |
          | 1428892912060            | {"content.type":"text/csv"}               | 222.205.101.211, Indianpolis, IN   |
          | 1428892912060            | {"content.type":"text/csv"}               | 24.4.216.155, Denver, CO           |
          | 1428892912060            | {"content.type":"text/csv"}               | 66.249.75.153, San Diego, CA       |
          | 1428892912060            | {"content.type":"text/csv"}               | 77.75.77.11, Austin, TX            |
          | 1428892981049            | {}                                        | 69.181.160.120, Los Angeles, CA    |
          +===========================================================================================================+
          Fetched 22 rows


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Having reviewed data, set a new format for the Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Drop the external Hive table
         - Recreate the Hive table with new schema
         
     * - Using CDAP
       - ``> set stream format ip2geo csv "ip string, city string, state string"``
          
     * -  
       - ::

          Successfully set format of stream 'ip2geo'

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve events from the Stream, in new format
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive command using Hive CLI
         - ``SELECT * FROM stream_ip2geo``
         
     * - Using CDAP
       - ``> execute 'select * from stream_ip2geo'``
          
     * -  
       - ::

          +================================================================================================================+
          | stream_ip2geo.ts:| stream_ip2geo.headers:      | stream_ip2geo.ip:| stream_ip2geo.city: | stream_ip2geo.state: |
          | BIGINT           | map<string,string>          | STRING           | STRING              | STRING               |
          +================================================================================================================+
          | 1428892912060    | {"content.type":"text/csv"} | 108.206.32.124   |  Santa Clara        |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 109.63.206.34    |  San Jose           |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 113.72.144.115   |  New York           |  New York            |
          | 1428892912060    | {"content.type":"text/csv"} | 123.125.71.19    |  Palo Alto          |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 123.125.71.27    |  Redwood            |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 123.125.71.28    |  Los Altos          |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 123.125.71.58    |  Mountain View      |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 142.54.173.19    |  Houston            |  TX                  |
          | 1428892912060    | {"content.type":"text/csv"} | 144.76.137.226   |  Dallas             |  TX                  |
          | 1428892912060    | {"content.type":"text/csv"} | 144.76.201.175   |  Bedminister        |  NJ                  |
          | 1428892912060    | {"content.type":"text/csv"} | 162.210.196.97   |  Milipitas          |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 188.138.17.205   |  Santa Barbara      |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 195.110.40.7     |  Orlando            |  FL                  |
          | 1428892912060    | {"content.type":"text/csv"} | 201.91.5.170     |  Tampa              |  FL                  |
          | 1428892912060    | {"content.type":"text/csv"} | 220.181.108.158  |  Miami              |  FL                  |
          | 1428892912060    | {"content.type":"text/csv"} | 220.181.108.161  |  Chicago            |  IL                  |
          | 1428892912060    | {"content.type":"text/csv"} | 220.181.108.184  |  Philadelphia       |  PA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 222.205.101.211  |  Indianpolis        |  IN                  |
          | 1428892912060    | {"content.type":"text/csv"} | 24.4.216.155     |  Denver             |  CO                  |
          | 1428892912060    | {"content.type":"text/csv"} | 66.249.75.153    |  San Diego          |  CA                  |
          | 1428892912060    | {"content.type":"text/csv"} | 77.75.77.11      |  Austin             |  TX                  |
          | 1428892981049    | {}                          | 69.181.160.120   |  Los Angeles        |  CA                  |
          +================================================================================================================+
          Fetched 22 rows


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Join data in the two Streams and retrieve selected events
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive command using Hive CLI
         - ``SELECT remote_host, city, state, request from stream_logEventStream join stream_ip2geo on (stream_logEventStream.remote_host = stream_ip2geo.ip) limit 10``
         
     * - Using CDAP
       - ``> execute 'select remote_host, city, state, request from stream_logEventStream join stream_ip2geo on (stream_logEventStream.remote_host = stream_ip2geo.ip) limit 10'``
          
     * -  
       - ::

          +===============================================================================================================+
          | remote_host: STRING          | city: STRING                 | state: STRING | request: STRING                 |
          +===============================================================================================================+
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /ajax/planStatusHistoryNeig |
          |                              |                              |               | hbouringSummaries.action?planKe |
          |                              |                              |               | y=COOP-DBT&buildNumber=284&_=14 |
          |                              |                              |               | 23341312519 HTTP/1.1            |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /rest/api/latest/server?_=1 |
          |                              |                              |               | 423341312520 HTTP/1.1           |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /ajax/planStatusHistoryNeig |
          |                              |                              |               | hbouringSummaries.action?planKe |
          |                              |                              |               | y=COOP-DBT&buildNumber=284&_=14 |
          |                              |                              |               | 23341312521 HTTP/1.1            |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /ajax/planStatusHistoryNeig |
          |                              |                              |               | hbouringSummaries.action?planKe |
          |                              |                              |               | y=COOP-DBT&buildNumber=284&_=14 |
          |                              |                              |               | 23341312522 HTTP/1.1            |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /rest/api/latest/server?_=1 |
          |                              |                              |               | 423341312523 HTTP/1.1           |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /ajax/planStatusHistoryNeig |
          |                              |                              |               | hbouringSummaries.action?planKe |
          |                              |                              |               | y=COOP-DBT&buildNumber=284&_=14 |
          |                              |                              |               | 23341312524 HTTP/1.1            |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /ajax/planStatusHistoryNeig |
          |                              |                              |               | hbouringSummaries.action?planKe |
          |                              |                              |               | y=COOP-DBT&buildNumber=284&_=14 |
          |                              |                              |               | 23341312525 HTTP/1.1            |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /rest/api/latest/server?_=1 |
          |                              |                              |               | 423341312526 HTTP/1.1           |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /ajax/planStatusHistoryNeig |
          |                              |                              |               | hbouringSummaries.action?planKe |
          |                              |                              |               | y=COOP-DBT&buildNumber=284&_=14 |
          |                              |                              |               | 23341312527 HTTP/1.1            |
          |---------------------------------------------------------------------------------------------------------------|
          | 69.181.160.120               |  Los Angeles                 |  CA           | GET /ajax/planStatusHistoryNeig |
          |                              |                              |               | hbouringSummaries.action?planKe |
          |                              |                              |               | y=COOP-DBT&buildNumber=284&_=14 |
          |                              |                              |               | 23341312528 HTTP/1.1            |
          +===============================================================================================================+
          Fetched 10 rows
        

Transforming Your Data
======================
- CDAP Adapters are high order compositions of programs that includes MapReduce, Workflow, Services
- Adapters provide pre-defined transformations to be applied on Streams or other datasets
- Adapters are re-usable, extendable, and  easily configured and managed
- Build your own adapters, using simple APIs
- In this example, we will apply a pre-defined transformation of converting data in
  streams to writing to ``TimePartitionedDatasets`` (in Avro format) that can be queried
  using either Hive or Impala


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Create a Stream-conversion Adaptor
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Write a custom consumer that reads from source (example: Kafka)
         - Write the data to HDFS
         - Create an external table in Hive called ``stream_ip2geo``
         - Orchestrate running the custom consumer periodically using **Oozie**
         - Keep track of last processed times
         
     * - Using CDAP
       - ``> create stream-conversion adapter logEventStreamConverter on logEventStream 
         frequency 1m format clf schema "remotehost string, remotelogname string, authuser 
         string, date string, request string, status int, contentlength int, referrer string, 
         useragent string"``       
          
     * -  
       - ::

          Successfully created adapter named 'logEventStreamConverter' with config 
          '{"type":"stream-conversion","properties":{"sink.name":"logEventStream.converted",
          "source.schema":"{...}","base.path":"logEventStream.converted"}}}'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - List the adaptors available in the CDAP instance
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Not available
         
     * - Using CDAP
       - ``> list adapters``
          
     * -  
       - ::

          +=============================================================================================================+
          | name                | type                | sources             | sinks               | properties          |
          +=============================================================================================================+
          | logEventStreamConve | stream-conversion   | [{"name":"logEventS | [{"name":"logEventS | {"sink.name":"logEv |
          | rter                |                     | tream","type":"STRE | tream.converted","t | entStream.converted |
          |                     |                     | AM","properties":{} | ype":"DATASET","pro | ","source.schema":" |
          |                     |                     | }]                  | perties":{"input.fo | {\"type\":\"record\ |
          |                     |                     |                     | rmat":"org.apache.a | ",\"name\":\"rec\", |
          |                     |                     |                     | vro.mapreduce.AvroK | \"fields\":[{\"name |
          |                     |                     |                     | eyInputFormat","exp | \":\"remotehost\",\ |
          |                     |                     |                     | lore.table.property | "type\":[\"string\" |
          |                     |                     |                     | .avro.schema.litera | ,\"null\"]},{\"name |
          |                     |                     |                     | l":"{\"type\":\"rec | \":\"remotelogname\ |
          |                     |                     |                     | ord\",\"name\":\"ev | ",\"type\":[\"strin |
          |                     |                     |                     | ent\",\"fields\":[{ | g\",\"null\"]},{\"n |
          |                     |                     |                     | \"name\":\"remoteho | ame\":\"authuser\", |
          |                     |                     |                     | st\",\"type\":[\"st | \"type\":[\"string\ |
          |                     |                     |                     | ring\",\"null\"]},{ | ",\"null\"]},{\"nam |
          |                     |                     |                     | \"name\":\"remotelo | e\":\"date\",\"type |
          |                     |                     |                     | gname\",\"type\":[\ | \":[\"string\",\"nu |
          |                     |                     |                     | "string\",\"null\"] | ll\"]},{\"name\":\" |
          |                     |                     |                     | },{\"name\":\"authu | request\",\"type\": |
          |                     |                     |                     | ser\",\"type\":[\"s | [\"string\",\"null\ |
          |                     |                     |                     | tring\",\"null\"]}, | "]},{\"name\":\"sta |
          |                     |                     |                     | {\"name\":\"date\", | tus\",\"type\":[\"i |
          |                     |                     |                     | \"type\":[\"string\ | nt\",\"null\"]},{\" |
          |                     |                     |                     | ",\"null\"]},{\"nam | name\":\"contentlen |
          |                     |                     |                     | e\":\"request\",\"t | gth\",\"type\":[\"i |
          |                     |                     |                     | ype\":[\"string\",\ | nt\",\"null\"]},{\" |
          |                     |                     |                     | "null\"]},{\"name\" | name\":\"referrer\" |
          |                     |                     |                     | :\"status\",\"type\ | ,\"type\":[\"string |
          |                     |                     |                     | ":[\"int\",\"null\" | \",\"null\"]},{\"na |
          |                     |                     |                     | ]},{\"name\":\"cont | me\":\"useragent\", |
          |                     |                     |                     | entlength\",\"type\ | \"type\":[\"string\ |
          |                     |                     |                     | ":[\"int\",\"null\" | ",\"null\"]}]}","so |
          |                     |                     |                     | ]},{\"name\":\"refe | urce.format.name":" |
          |                     |                     |                     | rrer\",\"type\":[\" | clf","frequency":"1 |
          |                     |                     |                     | string\",\"null\"]} | m","source.format.s |
          |                     |                     |                     | ,{\"name\":\"userag | ettings":"{}","sour |
          |                     |                     |                     | ent\",\"type\":[\"s | ce.name":"logEventS |
          |                     |                     |                     | tring\",\"null\"]}, | tream"}             |
          |                     |                     |                     | {\"name\":\"ts\",\" |                     |
          |                     |                     |                     | type\":\"long\"}]}" |                     |
          |                     |                     |                     | ,"dataset.class":"c |                     |
          |                     |                     |                     | o.cask.cdap.api.dat |                     |
          |                     |                     |                     | aset.lib.TimePartit |                     |
          |                     |                     |                     | ionedFileSet","expl |                     |
          |                     |                     |                     | ore.serde":"org.apa |                     |
          |                     |                     |                     | che.hadoop.hive.ser |                     |
          |                     |                     |                     | de2.avro.AvroSerDe" |                     |
          |                     |                     |                     | ,"base.path":"logEv |                     |
          |                     |                     |                     | entStream.converted |                     |
          |                     |                     |                     | ","explore.output.f |                     |
          |                     |                     |                     | ormat":"org.apache. |                     |
          |                     |                     |                     | hadoop.hive.ql.io.a |                     |
          |                     |                     |                     | vro.AvroContainerOu |                     |
          |                     |                     |                     | tputFormat","output |                     |
          |                     |                     |                     | .format":"org.apach |                     |
          |                     |                     |                     | e.avro.mapreduce.Av |                     |
          |                     |                     |                     | roKeyOutputFormat", |                     |
          |                     |                     |                     | "explore.input.form |                     |
          |                     |                     |                     | at":"org.apache.had |                     |
          |                     |                     |                     | oop.hive.ql.io.avro |                     |
          |                     |                     |                     | .AvroContainerInput |                     |
          |                     |                     |                     | Format","explore.en |                     |
          |                     |                     |                     | abled":"true"}}]    |                     |
          +=============================================================================================================+


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Load data into the Stream; it will automatically be converted  
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Write a custom consumer that reads from source (example: Kafka)
         - Write the data to HDFS
         - Create external table in Hive called ``stream_ip2geo``
         
     * - Using CDAP
       - ``> load stream logEventStream examples/resources/accesslog.txt``
          
     * -  
       - ::

          Successfully sent stream event to stream 'logEventStream'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - List available Datasets
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run **HDFS** commands using **HBase** shell
         - ``hbase shell> list``
         
     * - Using CDAP
       - Dataset that is time partitioned
          
     * -  
       - ``> list dataset instances``
         ::

          +======================================================================================================+
          | name                                  | type                                                         |
          +======================================================================================================+
          | logEventStream.converted              | co.cask.cdap.api.dataset.lib.TimePartitionedFileSet          |
          +======================================================================================================+

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Describe the converted Dataset
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive query using Hive CLI
         - ``'describe user_logEventStream_converted'`` 
         
     * - Using CDAP
       - ``> execute 'describe dataset_logEventStream_converted'``
          
     * -  
       - ::

          +==========================================================================================+
          | col_name: STRING                             | data_type: STRING   | comment: STRING     |
          +==========================================================================================+
          | remotehost                                   | string              | from deserializer   |
          | remotelogname                                | string              | from deserializer   |
          | authuser                                     | string              | from deserializer   |
          | date                                         | string              | from deserializer   |
          | request                                      | string              | from deserializer   |
          | status                                       | int                 | from deserializer   |
          | contentlength                                | int                 | from deserializer   |
          | referrer                                     | string              | from deserializer   |
          | useragent                                    | string              | from deserializer   |
          | ts                                           | bigint              | from deserializer   |
          | year                                         | int                 |                     |
          | month                                        | int                 |                     |
          | day                                          | int                 |                     |
          | hour                                         | int                 |                     |
          | minute                                       | int                 |                     |
          |                                              |                     |                     |
          | # Partition Information                      |                     |                     |
          | # col_name                                   | data_type           | comment             |
          |                                              |                     |                     |
          | year                                         | int                 |                     |
          | month                                        | int                 |                     |
          | day                                          | int                 |                     |
          | hour                                         | int                 |                     |
          | minute                                       | int                 |                     |
          +==========================================================================================+


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve the first two events from the converted data
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run Hive query using Hive CLI
         - ``SELECT * FROM user_logEventStream_converted LIMIT 2``
         
     * - Using CDAP
       - ``> execute 'SELECT * FROM dataset_logEventStream_converted LIMIT 2'``
          
     * -  
       - ::

          +==========================================================================================+
          | col_name: STRING                             | data_type: STRING   | comment: STRING     |
          +==========================================================================================+
          +==========================================================================================+


Building Real World Applications
================================
- Build Data Applications using simple-to-use CDAP APIs
- Compose complex applications consisting of Workflow, MapReduce, Realtime DAGs (Tigon) and Services
- Build using a collection of pre-defined data pattern libraries
- Deploy and manage complex data applications such as Web Applications

**Let's see how we would build a real-world application using CDAP:**

- *Wise App* performs Web analytics on access logs
- *WiseFlow* parses and computes pageview count per IP in realtime
- A MapReduce computes bounce counts: percentage of pages that *don’t* go to another page before exiting
- Service to expose the data 
- Unified platform for different processing paradigms

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Deploy a pre-built CDAP Application: Wise App
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Write and execute **MapReduce** using **Hadoop**
         - Separate environment for processing in real-time setup stack
         - Add ability to periodically copy datasets into **SQL** using **Sqoop**
         - Orchestrate the Mapreduce job using Oozie
         - Write an application to serve the data
         
     * - Using CDAP
       - ``> deploy app apps/cdap-wise-``\ |literal-cdap-apps-version|\ ``.jar``
          
     * -  
       - ::

          Successfully deployed application

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Describe application components
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Check Oozie
         - Check **YARN** Console
         
     * - Using CDAP
       - ``> describe app Wise``
          
     * -  
       - ::

          +=====================================================================+
          | type      | id                    | description                     |
          +=====================================================================+
          | Flow      | WiseFlow              | Wise Flow                       |
          | MapReduce | BounceCountsMapReduce | Bounce Counts MapReduce Program |
          | Service   | WiseService           |                                 |
          | Workflow  | WiseWorkflow          | Wise Workflow                   |
          +=====================================================================+

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Start the Application's Flow (for processing events)
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Set classpath in environment variable 
         - ``CLASSPATH=/my/classpath``
         - Run the command to start the yarn application
         - ``yarn jar /path/to/myprogram.jar``
         
     * - Using CDAP
       - ``> start flow Wise.WiseFlow``
          
     * -  
       - ::

          Successfully started Flow 'WiseFlow' of application 'Wise' with stored runtime arguments '{}

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Check the status of the Flow
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Retrieve the application ID
         - ``yarn application -list | grep "Wise.WiseFlow"``
         - Retrieve the status
         - ``yarn application -status <APP ID>``
         
     * - Using CDAP
       - ``> get flow status Wise.WiseFlow``
          
     * -  
       - ::

          RUNNING

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Ingest access log data into the Wise App Stream
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Write a custom consumer for Kafka that reads from source
         - Write the data to HDFS
         - Create external table in Hive called ``cdap_stream_logeventstream``
         
     * - Using CDAP
       - ``> load stream logEventStream examples/resources/accesslog.txt``
          
     * -  
       - ::

          Successfully sent stream event to stream 'logEventStream'  

.. highlight:: none
      
.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve 
       - 
       
 .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Navigate to the **Resource Manager UI**
         - Find the *Wise.WiseFlow* on UI
         - Click to see application logs
         - Find all the node managers for the application containers
         - Navigate to all the containers in separate tabs 
         - Click on container logs
         
     * - Using CDAP
       - ``> get flow logs Wise.WiseFlow``
    
     * -  
       - ::

          2015-04-15 09:22:53,775 - INFO  [FlowletRuntimeService
          STARTING:c.c.c.i.a.r.f.FlowletRuntimeService$1@110] - Initializing flowlet:
          flowlet=pageViewCount, instance=0, groupsize=1, namespaceId=default, applicationId=Wise,
          program=WiseFlow, runid=aae85671-e38b-11e4-bd5e-3ee74a48f4aa
          2015-04-15 09:22:53,779 - INFO  [FlowletRuntimeService
          STARTING:c.c.c.i.a.r.f.FlowletRuntimeService$1@117] - Flowlet initialized:
          flowlet=pageViewCount, instance=0, groupsize=1, namespaceId=default, applicationId=Wise,
          program=WiseFlow, runid=aae85671-e38b-11e4-bd5e-3ee74a48f4aa
          ...
          2015-04-15 10:07:54,708 - INFO  [FlowletRuntimeService
          STARTING:c.c.c.i.a.r.f.FlowletRuntimeService$1@117] - Flowlet initialized: flowlet=parser,
          instance=0, groupsize=1, namespaceId=default, applicationId=Wise, program=WiseFlow,
          runid=f4e0e52a-e391-11e4-a467-3ee74a48f4aa
          2015-04-15 10:07:54,709 - DEBUG [FlowletRuntimeService
          STARTING:c.c.c.i.a.r.AbstractProgramController@230] - Program started: WiseFlow:parser
          f4e0e52a-e391-11e4-a467-3ee74a48f4aa

.. highlight:: console

.. rubric:: Program Lifecycle

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Start the Wise Application Workflow to process ingested data
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Start the job using Oozie
         - ``oozie job -start <arguments>``
         
     * - Using CDAP
       - ``> start workflow Wise.WiseWorkflow``
          
     * -  
       - ::

          Successfully started Workflow 'WiseWorkflow' of application 'Wise' with stored runtime arguments '{}'


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Check the status of the Workflow 
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Get the workflow status from Oozie
         - ``oozie job -info <jobid>``
         
     * - Using CDAP
       - ``> get workflow status Wise.WiseWorkflow``
          
     * -  
       - ::

          RUNNING

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Start the WiseService that will be used to retrieve results
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Set classpath in environment variable 
         - ``CLASSPATH=/my/classpath``
         - Run the command to start the yarn application
         - ``yarn jar /path/to/myprogram.jar``
         
     * - Using CDAP
       - ``> start service Wise.WiseService``
          
     * -  
       - ::

          Successfully started Service 'WiseService' of application 'Wise' with stored runtime arguments '{}'

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Check the status of the Service
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Get the application ID
         - ``yarn application -list | grep "Wise.WiseService"``
         - Get the status
         - ``yarn application -status <APP ID>``
         
     * - Using CDAP
       - ``> get service status Wise.WiseService``
          
     * -  
       - ::

          RUNNING


.. rubric:: Serve the processed data in real time

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Discover the Wise Service's available endpoints for retrieving results
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Navigate to the resouce manager UI
         - Find the Wise.WiseService on UI
         - Click to the see application logs
         - Find all the node managers for the application containers
         - Navigate to all the containers in sepearate tabs 
         - Click on container logs
         
     * - Using CDAP
       - ``> get endpoints service Wise.WiseService``
          
     * -  
       - ::

          +=========================+
          | method | path           |
          +=========================+
          | GET    | /ip/{ip}/count |
          | POST   | /ip/{ip}/count |
          +=========================+


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve the count of a particular IP address (``69.181.160.120``)
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Discover the host and port where the service is running on by looking at the host 
           and port in the YARN logs or by writing a discovery client that is co-ordinated using **Zookeeper**
         - Run ``curl http://hostname:port/ip/69.181.160.120/count``
         
     * - Using CDAP
       - ``> call service Wise.WiseService GET /ip/69.181.160.120/count``
          
     * -  
       - ::

          +================================================+
          | status | headers            | body size | body |
          +================================================+
          | 200    | Content-Length : 4 | 4         | 6699 |
          |        | Connection : keep- |           |      |
          |        | alive              |           |      |
          |        | Content-Type : app |           |      |
          |        | lication/json      |           |      |
          +================================================+

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - List the Dataset instances
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run a command in HBase shell
         - ``hbase shell> list "cdap.user.*"``
         
     * - Using CDAP
       - ``> list dataset instances``
          
     * -  
       - ::

          +=================================================================+
          | name             | type                                         |
          +=================================================================+
          | pageViewStore    | co.cask.cdap.apps.wise.PageViewStore         |
          | bounceCountStore | co.cask.cdap.apps.wise.BounceCountStore      |
          +=================================================================+


.. rubric:: View bounce count results 

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Retrieve the first five pages with bounce counts and their statistics 
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Run a command in the Hive CLI
         - ``"SELECT * FROM dataset_bouncecountstore LIMIT 5"``
         
     * - Using CDAP
       - ``> execute 'SELECT * FROM dataset_bouncecountstore LIMIT 5'``
          
     * -  
       - ::

          +===============================================================================================+
          | dataset_bouncecountstore.uri: STRING   | dataset_bouncecountstore  | dataset_bouncecountstore |
          |                                        | .totalvisits: BIGINT      | .bounces: BIGINT         |
          +===============================================================================================+
          | /CDAP-DUT-50/index.php                 | 2                         | 2                        |
          |-----------------------------------------------------------------------------------------------|
          | /ajax/planStatusHistoryNeighbouringSum | 2                         | 2                        |
          | maries.action?planKey=CDAP-DUT&buildNu |                           |                          |
          | mber=50&_=1423398146659                |                           |                          |
          |-----------------------------------------------------------------------------------------------|
          | /ajax/planStatusHistoryNeighbouringSum | 2                         | 0                        |
          | maries.action?planKey=COOP-DBT&buildNu |                           |                          |
          | mber=284&_=1423341312519               |                           |                          |
          |-----------------------------------------------------------------------------------------------|
          | /ajax/planStatusHistoryNeighbouringSum | 2                         | 0                        |
          | maries.action?planKey=COOP-DBT&buildNu |                           |                          |
          | mber=284&_=1423341312521               |                           |                          |
          |-----------------------------------------------------------------------------------------------|
          | /ajax/planStatusHistoryNeighbouringSum | 2                         | 0                        |
          | maries.action?planKey=COOP-DBT&buildNu |                           |                          |
          | mber=284&_=1423341312522               |                           |                          |
          +===============================================================================================+
          Fetched 5 rows


.. rubric:: Stop Application and Delete From Server


.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Stop the Wise Service
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Find the yarn application ID from the following command
         - ``yarn application -list | grep "Wise.WiseService"``
         - Stop the application by running the following command
         - ``yarn application -kill <Application ID>``
         
     * - Using CDAP
       - ``> stop service Wise.WiseService``
          
     * -  
       - ::
       
          Successfully stopped Service 'WiseService' of application 'Wise'

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Stop the Wise Flow
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Find the yarn application ID from the following command
         - ``yarn application -list | grep "Wise.WiseFlow"``
         - Stop the application by running the following command
         - ``yarn application -kill <Application ID>``
         
     * - Using CDAP
       - ``> stop flow Wise.WiseFlow``
          
     * -  
       - ::
       
          Successfully stopped Flow 'WiseFlow' of application 'Wise'

.. container:: table-block

  .. list-table::
     :widths: 80 20
     :stub-columns: 1
     
     * - Delete the Application from the Server
       - 
       
  .. list-table::
     :widths: 15 85
     :class: triple-table
     :stub-columns: 1

     * - Without CDAP
       - - Delete the workflow from oozie
         - Remove the service jars and flow jars
         
     * - Using CDAP
       - ``> delete app Wise``
          
     * -  
       - ::
       
          Successfully deleted application 'Wise'


Summary
=======

.. list-table::
   :widths: 50 50
   :header-rows: 1

   * - Without CDAP
     - With CDAP 

   * - - Bring in different open source technologies, each with different design principles
       - Familiarize and learn how to operate the different technologies
       - Design specific architectures to wire in different components
       - Revisit everything whenever the technologies change
     - - Learn a single framework that works with multiple technologies
       - Abstraction of data in the Hadoop environment through logical representations of underlying data
       - Portability of applications through decoupling underlying infrastructures
       - Services and tools that enable faster application creation in development
       - Higher degrees of operational control in production through enterprise best practices
