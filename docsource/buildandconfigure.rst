.. _buildconfigdeploy:

Build, configure, and deploy
============================

These instructions assume the reader is familiar with the process of deploying
a KBase module, including the `runtime <https://github.com/kbase/bootstrap>`_
and `dev_container <https://github.com/kbase/dev_container>`_, and has access to
a system with the KBase runtime installed. These instructions are based on the
``kbase-image-v26`` runtime image.

Unlike many modules the WSS can be built and tested outside the
``dev_container``, but the ``dev_container`` is required to build and test the
scripts. These instructions are for deploying the server and so do not
address the scripts. Building outside the ``dev_container`` means the Makefile
uses several default values for deployment - if you wish to use other values
deploy from the ``dev_container`` as usual.

Build the workspace service
---------------------------

First checkout the ``dev_container``::

    /kb$ sudo git clone https://github.com/kbase/dev_container
    Cloning into 'dev_container'...
    remote: Counting objects: 1097, done.
    remote: Total 1097 (delta 0), reused 0 (delta 0), pack-reused 1097
    Receiving objects: 100% (1097/1097), 138.81 KiB, done.
    Resolving deltas: 100% (661/661), done.

.. note::
   In the v26 image, ``/kb`` is owned by ``root``. As an alternative to
   repetitive ``sudo`` s, ``chown`` ``/kb`` to the user.

Bootstrap and source the user environment file, which sets up Java and Perl
paths which the WSS build needs::

    /kb$ cd dev_container/
    /kb/dev_container$ sudo ./bootstrap /kb/runtime/
    /kb/dev_container$ source user-env.sh
    
Now the WSS may be built. If building inside the ``dev_container`` all the
dependencies from the ``DEPENDENCIES`` file are required, but to build outside
the ``dev_container``, only the ``jars`` and ``workspace_deluxe`` repos are
necessary::

    ~$ mkdir kb
    ~$ cd kb
    ~/kb$ git clone https://github.com/kbase/workspace_deluxe
    Cloning into 'workspace_deluxe'...
    remote: Counting objects: 21961, done.
    remote: Compressing objects: 100% (40/40), done.
    remote: Total 21961 (delta 20), reused 0 (delta 0), pack-reused 21921
    Receiving objects: 100% (21961/21961), 21.42 MiB | 16.27 MiB/s, done.
    Resolving deltas: 100% (13979/13979), done.

    ~/kb$ git clone https://github.com/kbase/jars
    Cloning into 'jars'...
    remote: Counting objects: 1466, done.
    remote: Total 1466 (delta 0), reused 0 (delta 0), pack-reused 1466
    Receiving objects: 100% (1466/1466), 59.43 MiB | 21.49 MiB/s, done.
    Resolving deltas: 100% (626/626), done.

    ~/kb$ cd workspace_deluxe/
    ~/kb/workspace_deluxe$ make
    *snip*
    
``make`` will build:

* A workspace client jar in ``/dist/client``
* A workspace server jar in ``/dist``
* This documentation in ``/docs``

.. note::
   If the build fails due to a sphinx error, sphinx may require an upgrade to
   >= 1.3::
   
       $ sudo pip install sphinx --upgrade

.. _servicedeps:

Service dependencies
--------------------

The WSS requires `MongoDB <https://mongodb.org>`_ 2.4+ to run. The WSS
may optionally use:

* `Shock <https://github.com/kbase/Shock>`_ as a file storage backend.
* The `Handle Service <https://github.com/kbase/handle_service>`_ 
  `b9de699 <https://github.com/kbase/handle_service/commit/b9de6991b851e9cd8fa9b5012db565f051e0894f>`_ +
  and `Handle Manager <https://github.com/kbase/handle_mngr>`_ 
  `3e60998 <https://github.com/kbase/handle_mngr/commit/3e60998fc22bb331e51b189ae1b71ebd54e58b90>`_ +
  to allow linking workspace objects to Shock nodes (see
  :ref:`shockintegration`).
  
The WSS has been tested against the auth2 branch of the KBase fork of Shock version 0.9.6
(e9f0e1618e265042bf5cb96429995b5e6ec0a06a), and against MongoDB versions 2.4.14, 2.6.11, 3.0.8,
and 3.2.1. 3.0+ versions were tested with and without the WiredTiger storage engine.
  
Please see the respective service documentation to set up and run the services
required.

.. note::
   The alternative to Shock as a file storage backend is MongoDB GridFS.
   GridFS is simpler to set up, but locks the entire database when writing
   files. Since the workspace can consume very large files, this can cause a
   significant impact on other database operations. 

Configuration
-------------

There are two sources of configuration data for the WSS. The first is contained
in the ``deploy.cfg`` file in the repository root (see
:ref:`configurationparameters`). Copy the provided ``deploy.cfg.example`` file to ``deploy.cfg``
to create the file. These parameters may change from invocation to
invocation of the workspace service. The second is contained in the workspace
MongoDB database itself and is set once by the configuration script (see
:ref:`configurationscript`).

.. warning::
   ``deploy.cfg`` contains several sets of credentials, and thus should be
   protected like any other file containing unencryted passwords or tokens.
   It is especially important to protect the password / token that the WSS uses
   to talk to Shock (``backend-secret`` or ``backend-token``) as if
   access to that account is lost, the new account owner has access to all
   the workspace object data, and recovery will be extremely time consuming
   (use shock admin account to change all the acls for every WSS owned object
   to the new account). At minimum, only the user that runs the WSS (which
   should **not** be ``root``) should have read access to ``deploy.cfg``. Also be
   aware that the ``deploy.cfg`` contents are copied to, by default,
   ``/kb/deployment/deployment.cfg`` when the workspace is deployed from the
   ``dev_container``.

.. _configurationparameters:

Configuration parameters
^^^^^^^^^^^^^^^^^^^^^^^^

mongodb-host
""""""""""""
**Required**: Yes

**Description**: Host and port of the MongoDB server, eg. localhost:27017

mongodb-database
""""""""""""""""
**Required**: Yes

**Description**: Name of the workspace MongoDB database

mongodb-user
""""""""""""
**Required**: If the MongoDB instance requires authorization

**Description**: Username for an account with readWrite access to the MongoDB
database

mongodb-pwd
"""""""""""
**Required**: If the MongoDB instance requires authorization

**Description**: Password for an account with readWrite access to the MongoDB
database

auth-service-url
""""""""""""""""
**Required**: Yes

**Description**: URL of the KBase authentication service

globus-url
""""""""""
**Required**: Yes

**Description**: URL of the Globus Nexus v1 authentication API

ignore-handle-service
"""""""""""""""""""""
**Required**: If not using handles

**Description**: Set to anything (``true`` is good) to not use handles. In this
case attempting to save an object with a handle will fail. Delete or leave
blank to use handles (the default). 

handle-service-url
""""""""""""""""""
**Required**: If using handles

**Description**: The URL of the Handle Service

handle-manager-url
""""""""""""""""""
**Required**: If using handles

**Description**: The URL of the Handle Manager

handle-manager-token
""""""""""""""""""""
**Required**: If using handles

**Description**: Credentials for the account approved for Handle Manager use

ws-admin
""""""""
**Required**: No

**Description**: the user name for a workspace administrator. This name, unlike
names added via the ``administer`` API call, is not permanently stored in the
database and thus the administrator will change if this name is changed and the
server restarted. This administrator cannot be removed by the ``administer``
API call.

backend-token
"""""""""""""
**Required**: If using Shock as the file backend

**Description**: Token for the file backend user account used by
the WSS to communicate with the backend. The user name is stored in the
database after being determined by the configuration script.

port
""""
**Required**: Yes

**Description**: The port on which the service will listen

server-threads
""""""""""""""
**Required**: Yes

**Description**: See :ref:`serverthreads`

min-memory
""""""""""
**Required**: Yes

**Description**: See :ref:`minmaxmemory`

max-memory
""""""""""
**Required**: Yes

**Description**: See :ref:`minmaxmemory`

temp-dir
""""""""
**Required**: Yes

**Description**: See :ref:`tempdir`

mongodb-retry
"""""""""""""
**Required**: No

**Description**: Startup MongoDB reconnect retry count. The workspace will try
to reconnect 1/s until this limit has been reached. This is useful for starting
the Workspace automatically after a server restart, as MongoDB can take quite a
while to get from start to accepting connections. The default is no retries.

dont-trust-x-ip-headers
"""""""""""""""""""""""
**Required**: No

**Description**: When ``true``, the server ignores the ``X-Forwarded-For`` and
``X-Real-IP`` headers. Otherwise (the default behavior), the logged IP address
for a request, in order of precedence, is 1) the first address in
``X-Forwarded-For``, 2) ``X-Real-IP``, and 3) the address of the client.

.. _configurationscript:

Configuration script
^^^^^^^^^^^^^^^^^^^^

Before starting the WSS for the first time, the database must be configured
with information about the type database and file backend. This information
travels with the MongoDB database because it is intrinsic to the overall
data store - once a type database and file backend are chosen, they cannot be
changed later without causing massive data inconsistency.

Prior to configuring the database, MongoDB must be running. If using Shock
as a backend, Shock must be running.

To configure the database, run the initialization script, which will step the
user through the process::

    ~/kb/workspace_deluxe$ cd administration/
    ~/kb/workspace_deluxe/administration$ ./initialize.py
    Current configuration file:
    mongodb-host=localhost
    mongodb-database=workspace
    handle-service-url=
    handle-manager-url=
    handle-manager-token=
    auth-service-url=https://kbase.us/services/auth/api/legacy/KBase/Sessions/Login/
    globus-url=https://kbase.us/services/auth/api/legacy/KBase
    ws-admin=workspaceadmin
    backend-token=
    port=7058
    server-threads=20
    min-memory=10000
    max-memory=15000
    temp-dir=ws_temp_dir
    mongodb-retry=0
    
    Keep this configuration? [y - keep]/n - discard: n
    Discarding current local configuration.
    Please enter value for mongodb-host: localhost
    Please enter value for mongodb-database: ws_db
    Does mongodb require authentication? [y - yes]/n - no: n
    Ok, commenting out authorization information.
    Attempting to connect to mongodb database "ws_db" at localhost... Connected.
    Please enter the name of the mongodb type database: ws_db_types
    Choose a backend:  [s - shock]/g - gridFS: s
    Please enter the url of the shock server: http://localhost:7044
    Please enter an authentication token for the workspace shock user account: [redacted]
    Validating token with auth server at https://kbase.us/services/auth/api/legacy/KBase/Sessions/Login/
    Successfully set DB configuration:
    type_db=ws_db_types
    backend=shock
    shock_location=http://localhost:7044/
    shock_user=gaprice
    
    Saving local configuration file:
    mongodb-host=localhost
    mongodb-database=ws_db
    handle-service-url=
    handle-manager-url=
    handle-manager-token=
    auth-service-url=https://kbase.us/services/auth/api/legacy/KBase/Sessions/Login/
    globus-url=https://kbase.us/services/auth/api/legacy/KBase
    ws-admin=workspaceadmin
    backend-token=[redacted]
    port=7058
    server-threads=20
    min-memory=10000
    max-memory=15000
    temp-dir=ws_temp_dir
    mongodb-retry=0
    
    Configuration saved.
    
Note that the configuration script will only alter the ``mongodb-*`` and
``backend-secret`` parameters. Other parameters must be altered through
manually editing ``deploy.cfg``.

Also, do not, under any circumstances, use ``kbasetest`` as the account with
which the WSS will communicate with Shock.

Once the database is started and ``deploy.cfg`` is filled in to the user's
satisfaction, the server may be deployed and started.

Deploy and start the server
---------------------------

To avoid various issues when deploying, ``chown`` the deployment directory
to the user. Alternatively, chown ``/kb/`` to the user, or deploy as root.
::

    ~/kb/workspace_deluxe$ sudo mkdir /kb/deployment
    ~/kb/workspace_deluxe$ sudo chown ubuntu /kb/deployment
    ~/kb/workspace_deluxe$ make deploy
    *snip*
    Makefile:53: Warning! Running outside the dev_container - scripts will not be deployed or tested.

Since the service was deployed outside of the ``dev_container``, the service
needs to be told where ``deploy.cfg`` is located. When built in the
``dev_container``, the contents of ``deploy.cfg`` are automatically copied to
a global configuration and this step is not necessary.
::

    ~/kb/workspace_deluxe$ export KB_DEPLOYMENT_CONFIG=~/kb/workspace_deluxe/deploy.cfg

Next, start the service. If using Shock or the Handle services, ensure they are
up and running before starting the WSS.

The workspace service can be run under multiple servlet 3.1 compliant containers. The
first set of instructions below describe starting/stopping using the Glassfish 3.1.x
servlet container. The Glassfish 3.1.x branch no longer has public support and is scheduled
to be end of lifed entirely in 2019, as a consequence after January 2018, Tomcat 8.5.x
will be the supported servlet engine. The second set of instructions detail how to start
and stop workspace under Tomcat. The directions up to this point for configuration files,
environment variables and dependent services remain the same for both Glassfish and Tomcat.

**Run under Glassfiash 3.1.2**
::

    ~/kb/workspace_deluxe$ /kb/deployment/services/workspace/start_service 
    Creating domain Workspace at /kb/deployment/services/workspace/glassfish_domain
    Using default port 4848 for Admin.
    Using default port 8080 for HTTP Instance.
    *snip*
    No domain initializers found, bypassing customization step
    Domain Workspace created.
    Domain Workspace admin port is 4848.
    Domain Workspace allows admin login as user "admin" with no password.
    Command create-domain executed successfully.
    Starting domain Workspace
    Waiting for Workspace to start .......
    Successfully started the domain : Workspace
    domain  Location: /kb/deployment/services/workspace/glassfish_domain/Workspace
    Log File: /kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log
    Admin Port: 4848
    Command start-domain executed successfully.
    Removing options []
    Setting option -Xms10000m
    Removing options ['-Xmx512m']
    Setting option -Xmx15000m
    Restarting Workspace, please wait
    Successfully restarted the domain
    Command restart-domain executed successfully.
    Creating property KB_DEPLOYMENT_CONFIG=/home/ubuntu/kb/workspace_deluxe/deploy.cfg
    Command create-system-properties executed successfully.
    Command create-virtual-server executed successfully.
    Command create-threadpool executed successfully.
    Command create-http-listener executed successfully.
    server.network-config.network-listeners.network-listener.http-listener-7058.thread-pool=thread-pool-7058
    Command set executed successfully.
    server.network-config.protocols.protocol.http-listener-7058.http.timeout-seconds=1800
    Command set executed successfully.
    Application deployed with name app-7058.
    Command deploy executed successfully.
    The server started successfully.

Stop the service::

    ~/kb/workspace_deluxe$ /kb/deployment/services/workspace/stop_service 
    Domain Workspace exists at /kb/deployment/services/workspace/glassfish_domain, skipping creation
    Domain Workspace is already running on port 4848
    Command undeploy executed successfully.
    Command delete-http-listener executed successfully.
    Command delete-threadpool executed successfully.
    Command delete-virtual-server executed successfully

Note that the ``stop_service`` script leaves the Glassfish server running.
``kill`` the Glassfish instance to completely shut down the server.

If any problems occur, check the glassfish logs (by default at
``/kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log``
and system logs (on Ubuntu, at ``/var/log/syslog``). If the JVM can't start at
all (for instance, if the JVM can't allocate enough memory), the glassfish
logs are the most likely place to look. If the JVM starts but the workspace
application does not, the system logs should provide answers.

**Run under Tomcat 8.5.x**

As of January 2018, Tomcat 8.5.24 is the production/stable release of Tomcat. The server
can be downloaded from <https://tomcat.apache.org/download-80.cgi>. The workspace service
should be able to run on older and newer versions of Tomcat that support the Servlet 3.1
specification. For production purposes, it is not recommended to run Workspace on versions
of Tomcat that do not support Non-Blocking IO due to potential performance bottlenecks under
high concurrency.

Download Tomcat and unzip into working directory::

    Steves-MBP:workspace_deluxe sychan$ cd tmp
    Steves-MBP:tmp sychan$ wget http://apache.mirrors.ionfish.org/tomcat/tomcat-8/v8.5.24/bin/apache-tomcat-8.5.24.tar.gz
    --2018-01-18 09:40:34--  http://apache.mirrors.ionfish.org/tomcat/tomcat-8/v8.5.24/bin/apache-tomcat-8.5.24.tar.gz
    Resolving apache.mirrors.ionfish.org... 38.126.148.232
    Connecting to apache.mirrors.ionfish.org|38.126.148.232|:80... connected.
    HTTP request sent, awaiting response... 200 OK
    Length: 9487006 (9.0M) [application/x-gzip]
    Saving to: ‘apache-tomcat-8.5.24.tar.gz’

    apache-tomcat-8.5.24.tar.gz                       100%[==========================================================================================================>]   9.05M  1.01MB/s    in 9.1s    

    2018-01-18 09:40:47 (1018 KB/s) - ‘apache-tomcat-8.5.24.tar.gz’ saved [9487006/9487006]

    Steves-MBP:tmp sychan$ tar xzf apache-tomcat-8.5.24.tar.gz 
    Steves-MBP:tmp sychan$ ls apache-tomcat-8.5.24
    LICENSE		NOTICE		RELEASE-NOTES	RUNNING.txt	bin		conf		lib		logs		temp		webapps		work
    Steves-MBP:tmp sychan$ 

The next step is to remove the default Tomcat distributed root servlet container and replace it
with the workspace WAR file generated by make, so that the the only code running is the workspace service.

Update Tomcat ROOT warfile::

    Steves-MBP:tmp sychan$ cd apache-tomcat-8.5.24
    Steves-MBP:apache-tomcat-8.5.24 sychan$ ls
    LICENSE		NOTICE		RELEASE-NOTES	RUNNING.txt	bin		conf		lib		logs		temp		webapps		work
    Steves-MBP:apache-tomcat-8.5.24 sychan$ cd webapps/
    Steves-MBP:webapps sychan$ ls
    ROOT		docs		examples	host-manager	manager
    Steves-MBP:webapps sychan$ rm -rf *
    Steves-MBP:webapps sychan$ cp ~/src/workspace_deluxe/dist/WorkspaceService.war ROOT.war
    Steves-MBP:webapps sychan$ ls -l
    total 39704
    -rw-r--r--  1 sychan  staff  20324677 Jan 18 09:50 ROOT.war
    Steves-MBP:webapps sychan$ 

At this point, we can start Tomcat and it will deploy the WorkspaceService.war file as the
root handler on the default listener port of 8080. However the directives in the
KB_DEPLOYMENT_CONFIG file for *port*, *server-threads*, *min-memory* and *max_memory* are not
implemented in the WARfile code, but in glassfish wrapper scripts. These will need to be
updated manually in the Tomcat configuration files.

*Updating the listener port*

Under the Tomcat root there is a conf/server.xml file, update the following stanza, replacing
the port="8080" assignment with the appropriate port

conf/server.xml::

    <!-- A "Connector" represents an endpoint by which requests are received
            and responses are returned. Documentation at :
            Java HTTP Connector: /docs/config/http.html
            Java AJP  Connector: /docs/config/ajp.html
            APR (HTTP/AJP) Connector: /docs/apr.html
            Define a non-SSL/TLS HTTP/1.1 Connector on port 8080
    -->
    <Connector port="8080" protocol="HTTP/1.1"
                connectionTimeout="20000"
                redirectPort="8443" />

Note that in a environment with high load, the protocol="HTTP/1.1" argument
should be replaced with protocol="org.apache.coyote.http11.Http11Nio2Protocol" to use
the non-blocking IO connector.

*Updating the min/max memory for the JVM*

JVM configurations are handled via environment variables defined a bin/setenv.sh file
that needs to be defined by the developer. Create the following file under the Tomcat
root, and substitute the appropriate values for min_memory and max_memory into the
-Xms and -Xmx flags for JAVA_OPTS. The given values here are reasonable for a test
service on a developer workstation. In production typically 10G is the minimum and
15G is the maximum.

bin/setenv.sh::

    #!/bin/sh
    #
    JAVA_OPTS="-Djava.awt.headless=true -server -Xms1000m -Xmx3000m -XX:+UseG1GC"

*Configure the size of the thread pool*

The thread pool is configured in the conf/server.xml file in the following stanza.

conf/server.xml::

    <!--The connectors can use a shared executor, you can define one or more named thread pools-->
    <!--
    <Executor name="tomcatThreadPool" namePrefix="catalina-exec-"
        maxThreads="20" minSpareThreads="4"/>

The default value is 150 maxThreads. The workspace service is a relatively heavyweight service.
Typically we only use 20 max threads.

Having made any necessary configuration changes, we can start Tomcat using the standard admin
scripts under the bin/ directory. To start Tomcat server in the terminal foreground in order to
observe any server messages, we can use "bin/catalina.sh run". Output very similar to the
following should come up:

Start Tomcat with Workspace service::

    18-Jan-2018 19:55:12.385 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Server built:          Sep 3 2017 17:51:58 UTC
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Server number:         8.5.14.0
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log OS Name:               Linux
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log OS Version:            4.9.49-moby
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Architecture:          amd64
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Java Home:             /usr/lib/jvm/java-8-openjdk-amd64/jre
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log JVM Version:           1.8.0_141-8u141-b15-1~deb9u1-b15
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log JVM Vendor:            Oracle Corporation
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log CATALINA_BASE:         /kb/deployment/services/workspace/tomcat
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log CATALINA_HOME:         /usr/share/tomcat8
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.util.logging.config.file=/kb/deployment/services/workspace/tomcat/conf/logging.properties
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.awt.headless=true
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Xms1000m
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Xmx3000m
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -XX:+UseG1GC
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djdk.tls.ephemeralDHKeySize=2048
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.protocol.handler.pkgs=org.apache.catalina.webresources
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Dcatalina.base=/kb/deployment/services/workspace/tomcat
    18-Jan-2018 19:55:12.390 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Dcatalina.home=/usr/share/tomcat8
    18-Jan-2018 19:55:12.390 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.io.tmpdir=/kb/deployment/services/workspace/tomcat/temp
    18-Jan-2018 19:55:12.390 INFO [main] org.apache.catalina.core.AprLifecycleListener.lifecycleEvent The APR based Apache Tomcat Native library which allows optimal performance in production environments was not found on the java.library.path: /usr/java/packages/lib/amd64:/usr/lib/x86_64-linux-gnu/jni:/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:/usr/lib/jni:/lib:/usr/lib
    18-Jan-2018 19:55:12.491 INFO [main] org.apache.coyote.AbstractProtocol.init Initializing ProtocolHandler ["http-nio2-8080"]
    18-Jan-2018 19:55:12.498 WARNING [main] org.apache.tomcat.util.net.Nio2Endpoint.bind The NIO2 connector requires an exclusive executor to operate properly on shutdown
    18-Jan-2018 19:55:12.606 INFO [main] org.apache.catalina.startup.Catalina.load Initialization processed in 606 ms
    18-Jan-2018 19:55:12.637 INFO [main] org.apache.catalina.core.StandardService.startInternal Starting service Catalina
    18-Jan-2018 19:55:12.638 INFO [main] org.apache.catalina.core.StandardEngine.startInternal Starting Servlet Engine: Apache Tomcat/8.5.14 (Debian)
    18-Jan-2018 19:55:12.664 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployWAR Deploying web application archive /kb/deployment/services/workspace/tomcat/webapps/ROOT.war
    18-Jan-2018 19:55:14.312 INFO [localhost-startStop-1] org.apache.jasper.servlet.TldScanner.scanJars At least one JAR was scanned for TLDs yet contained no TLDs. Enable debug logging for this logger for a complete list of JARs that were scanned but no TLDs were found in them. Skipping unneeded JARs during scanning can improve startup time and JSP compilation time.
    MongoDB reconnect value is 0
    Warning - the Globus url uses insecure http. https is recommended.
    Warning - the Auth Service url uses insecure http. https is recommended.
    Warning - the Handle Service url uses insecure http. https is recommended.
    Warning - the Handle Manager url uses insecure http. https is recommended.
    Starting server using connection parameters:
    mongodb-host=ci-mongo
    mongodb-database=workspace
    mongodb-user=
    globus-url=http://auth:8080/api/legacy/globus
    auth-service-url=http://auth:8080/api/legacy/KBase
    handle-service-url=http://handle_service:8080/
    handle-manager-url=http://handle_manager:8080/
    listeners=us.kbase.workspace.modules.SearchPrototypeEventHandlerFactory,us.kbase.workspace.modules.KnowledgeEnginePrototypeEventHandlerFactory
    Temporary file location: ws_temp_dir
    Initialized Shock backend
    Started workspace server instance 1. Free mem: 936900632 Total mem: 1048576000, Max mem: 3145728000
    18-Jan-2018 19:55:15.574 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployWAR Deployment of web application archive /kb/deployment/services/workspace/tomcat/webapps/ROOT.war has finished in 2,910 ms
    18-Jan-2018 19:55:15.586 INFO [main] org.apache.coyote.AbstractProtocol.start Starting ProtocolHandler ["http-nio2-8080"]
    18-Jan-2018 19:55:15.588 INFO [main] org.apache.catalina.startup.Catalina.start Server startup in 2981 ms

The tomcat service can be stopped by entering "ctrl-C" from the terminal where tomcat is
running the foreground. An alternative that has Tomcat running the background
would be to start Tomcat in the background using "catalina.sh start|stop" commands.

Catalina.sh start/stop::

    120:apache-tomcat-8.5.24 sychan$ bin/catalina.sh start
    Using CATALINA_BASE:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_HOME:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_TMPDIR: /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/temp
    Using JRE_HOME:        /Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home
    Using CLASSPATH:       /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/bootstrap.jar:/Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/tomcat-juli.jar
    Tomcat started.
    120:apache-tomcat-8.5.24 sychan$ bin/catalina.sh stop
    Using CATALINA_BASE:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_HOME:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_TMPDIR: /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/temp
    Using JRE_HOME:        /Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home
    Using CLASSPATH:       /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/bootstrap.jar:/Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/tomcat-juli.jar
    120:apache-tomcat-8.5.24 sychan$ 
