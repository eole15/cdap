.. meta::
    :author: Cask Data, Inc.
    :description: Users' Manual
    :copyright: Copyright © 2015 Cask Data, Inc.


.. _users-index:

==================================================
CDAP Users’ Manual
==================================================

.. highlight:: console

Overview
========


Purpose: Manual for people who are using CDAP.

What is an ETL Application? [Users’ Manual]

  Source -> [Transformation] -> Sink
  
  ETL "pipeline"
  One pipeline per Application
  
Supported Use Cases [Users’ Manual]

  Batch
  
  Realtime
  

ETL Application Components [Users’ Manual]

  Sources
  
    Batch
      StreamSource
      PartitionFileSource
      TableSource
      DBInputSource
      
    Realtime
      Kafka 0.7/0.8
      Java Message Service (JMS)
      Twitter (Twitter4J library)

  Sinks
  
    Batch
      PartitionFileSink
      TableSink
      DBSink
      
    Realtime
      StreamSink
      TableSink
      KVTableSink (Key Value Table Sink)
      CubeSink
      
    Sink as Source for additional Applications/pipelines
      
  Transformations

    Filters (UI:"IdentityTransform")
      Filter based on a criteria [tbd]
    
    Projection (UI:"ProjectionTransform")
      Dropping Columns
      Renaming Columns
      Converting Columns
      
    Custom
      Uses Javascript

Creating an ETL Application [Users’ Manual]

  Created with:
    UI
    CLI
    Java
    Java Client ?
    Other Clients (Javascript, Python, Ruby) ?
    
  Steps
    UI
      Start with template
      Using an Adaptor template
      Set Source
      Set Sink
      Set Transformation(s), if any
      Operating
    CLI
      ??

Creating Custom ETL Components [Developers’ Manual?]

  Components
    Sinks
    Sources
    Transformations
    Structured Records

  Creating a Custom Adaptor-template
    CLI
    REST API
    Java Client API
    
    Accessing existing Servers and Sinks
    
  Creating Custom Transforms
    Written in Javascript
  
  Creating a Plugin
    Written in Java

      




============







For ETL use cases we are specifically building, what is called as, ETL Templates with
which you can easily create and manage ETL Pipelines. This will be available in 3.0 (our
upcoming release). You can check out the JIRA and design details here :
https://issues.cask.co/browse/CDAP-1753.

Brief overview: ETL Templates will allow users to create and manage ETL Pipelines without
writing any code. Drag and drop the source, transforms, sink you need on the UI. Configure
them and you are good to go.

For example, say you want to read a table from MySQL, perform some filtering or projection
and then write to CDAP Datasets. You can achieve this by using a simple JSON configuration
file and curl request (or even better just drag and drop on the UI).

{
  "config" : 
            {
                "schedule" : "cron entry - run every hour", 
                "source" : {
                                 "name":"DBSource", 
                                  "properties" : { "JDBCUrl" : "xyz", "Table" : "myTable" } 
                                 }, 
                 "sink" : { 
                                 "name" : "TableSink", 
                                  "properties" : { "tableName" : "profilesDataset" } 
                              }, 
                 "transforms" : [ 
                                        { 
                                           "name":"ProjectionTransform",  
                                           "properties" : { "dropField" : "age" }
                                        } 
                                        ]
            }
}

This will schedule a Batch Job (in this release, a MapReduce Job) to run every hour and it
will read from DB, perform projection and write to CDAP Tables. Similarly we you can also
do Realtime ETL pipelines, such as, say reading from Kafka/JMS, doing some transforms and
writing them to CDAP Streams etc.

There will be a number of sources, sinks and transforms that will be available right out
of the box for you to use. If you want to build your own source, sink or transform, you
can do so by using the ETL API that we provide and you can build your own "plugins" (and I
hope you will choose to contribute it back to CDAP :) ) and drop into CDAP installation
and they will be available for you to use for building ETL pipelines.

The concept of templates is generic can be used to build abstractions for other use cases.
Watch out for these features in our upcoming 3.0 release and follow the JIRA as well. Feel
free to reach out with any questions that you might have.
