This readme contains information about building and testing EAP.

Building
---------
To run a basic EAP build from the root directory of AS:

    ./build.sh

This build will only use the Mead (product) Maven repository which does not include 
all the necessary dependencies for running the testsuite.  By default, the
build will use the directory "./local-repo-eap" as the local Maven repository.
This directory can be overridden by setting "maven.repo.local" from the command line.

    ./build.sh -Dmaven.repo.local=./my-local-repo

Running the testsuite
----------------------
The arquillian and testsuite modules require access to the public JBoss.org and Maven central
repositories, therefore these modules are disabled by default.  To enable the arquillian and
testsuite modules, use the property "public-repos" to specify that the public Maven repositories
have been configured.

    ./build.sh -Dpublic-repos

This will run the full build and will activate the public Maven repositories
(central and jboss.org).

To run only a specific testsuite module or an individual test, you will still need
to run build.sh from the root directory.  Individual testsuite modules can be run
by setting the directory using the "-pl" option.  The basic integration tests can
be run using this option.

    ./build.sh -Dpublic-repos -DallTests -pl testsuite/integration/basic

To run a single test, use the -Dtest option to Maven
From the root directory:

    ./build.sh -Dpublic-repos -DallTests -pl testsuite/integration/basic -Dtest=XercesUsageTestCase


