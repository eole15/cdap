.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2014-2015 Cask Data, Inc.

:hide-toc: true

.. _operations-index:

==========
Operations
==========

.. toctree::
   :maxdepth: 1
   
    Logging <logging>
    Metrics <metrics>
    Preferences and Runtime Arguments <preferences>
    Scaling Instances <scaling-instances>
    Resource Guarantees in YARN <resource-guarantees>
    CDAP Console <cdap-console>
    Master Services Logback <logback>
    Transaction Service Maintenance <tx-maintenance>
    Troubleshooting <troubleshooting>

.. |logging| replace:: **Logging:**
.. _logging: logging.html

- |logging|_ Covers **CDAP support for logging** through standard SLF4J (Simple Logging Facade for Java) APIs.


.. |metrics| replace:: **Metrics:**
.. _metrics: metrics.html

- |metrics|_ CDAP collects **metrics about the application’s behavior and performance**.
  

.. |preferences| replace:: **Preferences and Runtime Arguments:**
.. _preferences: preferences.html

- |preferences|_ **Preferences** provide the ability to save configuration information. 
  Flows, MapReduce programs, Services, Workflows and Workers can receive **runtime arguments.**


.. |scaling-instances| replace:: **Scaling Instances:**
.. _scaling-instances: scaling-instances.html

- |scaling-instances|_ Covers **querying and setting the number of instances of Flowlets.** 


.. |resource-guarantees| replace:: **Resource Guarantees:**
.. _resource-guarantees: resource-guarantees.html

- |resource-guarantees|_ Providing resource guarantees **for CDAP Programs in YARN.**


.. |cdap-console| replace:: **CDAP Console:**
.. _cdap-console: cdap-console.html

- |cdap-console|_ The CDAP Console is available for **deploying, querying and managing CDAP.** 


.. |logback| replace:: **Master Services Logging Configuration:**
.. _logback: logback.html

- |logback|_ This section describes the logging configuration used by CDAP Master Services.


.. |tx-maintenance| replace:: **Transaction Service Maintenance:**
.. _tx-maintenance: tx-maintenance.html

- |tx-maintenance|_ Periodic maintenance of **Transaction Service.**


.. |troubleshooting| replace:: **Troubleshooting:**
.. _troubleshooting: troubleshooting.html

- |troubleshooting|_ Selected examples of potential **problems and possible resolutions.**


.. rubric:: Command Line Interface

Most of the administrative operations are also available more conveniently through the
Command Line Interface. See :ref:`reference:cli` in the 
:ref:`CDAP Reference Manual<reference:reference-index>` for details.


.. rubric:: Getting a Health Check

.. _operations-health-check:

.. highlight:: console

Administrators can check the health of various services in the system.
(In these examples, substitute for ``<host>`` the host name or IP address of the CDAP server.)

- To retrieve the **health check of the CDAP Console**, make a GET request to the URI::

    http://<host>:9999/status

- To retrieve the **health check of the CDAP Router**, make a GET request to the URI::

    http://<host>:10000/status

- To retrieve the **health check of the CDAP Authentication Server**, make a GET request to
  the URI::
  
    http://<host>:10009/status

On success, the calls return a valid HTTP response with a 200 code.

- To retrieve the **health check of all the services running in YARN**, make a GET request
  to the URI::
  
    http://<host>:10000/v3/system/services

  On success, the call returns a JSON string with component names and their corresponding 
  statuses (reformatted to fit)::
  
    [{"name":"appfabric","description":"Service for managing application
      lifecycle.","status":"OK","logs":"OK","min":1,"max":1,"requested":1,"provisioned":1},
     {"name":"dataset.executor","description":"Service to perform Dataset
      operations.","status":"OK","logs":"OK","min":1,"max":1,"requested":1,"provisioned":1},
     {"name":"explore.service","description":"Service to run Ad-hoc
      queries.","status":"OK","logs":"OK","min":1,"max":1,"requested":1,"provisioned":1},
     {"name":"log.saver","description":"Service to collect and store
      logs.","status":"OK","logs":"NOTOK","min":1,"max":1,"requested":1,"provisioned":1},
     {"name":"metrics","description":"Service to handle metrics
      requests.","status":"OK","logs":"OK","min":1,"max":1,"requested":1,"provisioned":1},
     {"name":"metrics.processor","description":"Service to process application and system
      metrics.","status":"OK","logs":"NOTOK","min":1,"max":1,"requested":1,"provisioned":1},
     {"name":"streams","description":"Service that handles stream data
      ingestion.","status":"OK","logs":"OK","min":1,"max":1,"requested":1,"provisioned":1},
     {"name":"transaction","description":"Service that maintains transaction
      states.","status":"OK","logs":"NOTOK","min":1,"max":1,"requested":1,"provisioned":1}]
