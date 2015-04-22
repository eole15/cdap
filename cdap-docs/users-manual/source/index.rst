.. meta::
    :author: Cask Data, Inc.
    :description: Users' Manual
    :copyright: Copyright © 2015 Cask Data, Inc.


.. _users-index:

==================================================
CDAP Users’ Manual
==================================================

.. highlight:: console

Outline
========

Users’ Manual Sections
  Purpose: For people who are using CDAP and existing ETL Applications

Developers’ Manual Sections
  Purpose: For people writing new ETL Applications and ETL Components


What is an ETL Application? (Users’ Manual)

* Source -> (zero or more Transformations) -> Sink
* ETL "pipeline"
** One pipeline per Application


Supported Use Cases (Users’ Manual)

* Batch
* Realtime


ETL Application Components (Users’ Manual)

* Sources
** Batch
*** StreamSource
*** PartitionFileSource
*** TableSource
*** DBInputSource (MySQLBatchSource?)
*** DatasetSource
*** KafkaSource  0.7/0.8
** Realtime
*** Kafka 0.7/0.8
*** Java Message Service (JMS)
*** Twitter (Twitter4J library)
*** Database (stream new records)
* Sinks
** Batch
*** PartitionFileSink
*** TableSink
*** DBSink
** Realtime
*** StreamSink
*** TableSink
*** KeyValueTableSink (KVTableSink)
*** CubeSink
* Sink as Source for additional Applications/pipelines   
* Transformations
** Filters (UI:"IdentityTransform")
*** Filter based on a criteria [tbd]
** Projection (UI:"ProjectionTransform")
*** Dropping Columns
*** Renaming Columns
*** Converting Columns
**Custom Transforms
** Uses Javascript?
** Link to Dev Manual?


Creating an ETL Application (Users’ Manual)

* Can be created with:
** UI
** CLI
** Java
** Java Client ?
** Other Clients (Javascript, Python, Ruby) ?
* Steps
** With UI
 ** Start with an App-Template
 ** Set Source
 ** Set Sink
 ** Set Transformation(s), if any


Operating An ETL Application (Users’ Manual)

* Lifecycle
** List App-Templates
** Status
** Set Instances
** Start
** Stop
** Delete/Remove
** Update ?
* Operations
** With UI ??
** With CLI ??
** With RESTful API ??
      