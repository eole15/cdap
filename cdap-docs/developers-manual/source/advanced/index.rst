.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2014-2015 Cask Data, Inc.

:hide-toc: true

============================================
Advanced Topics
============================================

.. toctree::
   :maxdepth: 1
   
    Best Practices <best-practices>
    Adapters <adapters>
    Creating Custom ETL Applications <etl>
    Application Logback <application-logback>

This section of the documentation includes articles that cover advanced topics on CDAP that
will be of interest to developers who want a deeper dive into CDAP:

.. |best-practices| replace:: **Best Practices:**
.. _best-practices: best-practices.html

- |best-practices|_ Suggestions when developing a CDAP application.


.. |adapters| replace:: **Adapters:**
.. _adapters: adapters.html

- |adapters|_ Adapters connect a data source to a data sink.
  CDAP currently provides a stream conversion Adapter that regularly reads data from a Stream and
  writes it to a ``TimePartitionedFileSet``, allowing it to be queried through Hive and Impala.


.. |etl| replace:: **Creating Custom ETL Applications:**
.. _etl: etl.html

- |etl|_ Covers creating custom ETL Applications and Components, including custom
  Sources, Sinks, Transformations, and Plugins, that connect data sources to data sinks.


.. |application-logback| replace:: **Application Logback:**
.. _application-logback: application-logback.html

- |application-logback|_ Adding a custom logback to a CDAP application.