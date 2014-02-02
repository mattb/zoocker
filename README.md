Zoocker
-------

Scans the Docker API and maintains ephemeral nodes in Zookeeper for all containers and their mapped ports. Intended to run inside a Docker container using the included Dockerfile. Like a really minimal Synapse - https://github.com/airbnb/synapse

Build:

$ docker build -t zoocker .

Run Zookeeper docker container:

$ docker run -d -p 2181 -name zk paulczar/zookeeper-3.4.5 /opt/zookeeper-3.4.5/bin/zkServer.sh start-foreground

Run Zoocker (with /var/run/docker.sock mapped into the container to give API access, and with a Zookeeper container configured using docker links):

$ docker -v /var/run/docker.sock:/docker.sock -e EXTERNAL_HOST=192.168.100.100 -link zk:zk zoocker
