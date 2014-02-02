FROM stackbrew/ubuntu:raring

ENV DEBIAN_FRONTEND noninteractive

RUN echo "deb http://us.archive.ubuntu.com/ubuntu raring main universe" > /etc/apt/sources.list
RUN echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu raring main" > /etc/apt/sources.list.d/webupd8team-java-raring.list
RUN apt-get update

# accept java license
RUN echo debconf shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN echo debconf shared/accepted-oracle-license-v1-1 seen true | /usr/bin/debconf-set-selections

# basic stuff
RUN apt-get install -y --force-yes git curl unzip oracle-java7-installer libjansi-java

ADD . /opt/

RUN cd /opt ; java -Dsbt.log.noformat=true -jar sbt-launch.jar stage

RUN apt-get install -yq supervisor socat python-pip && pip install supervisor-stdout

ADD ./supervisord.conf /etc/supervisor/conf.d/supervisord.conf

CMD ["supervisord","-n"]
