FROM ubuntu:14.04

RUN apt-get update && apt-get upgrade -y
RUN apt-get install -y openjdk-7-jdk

# config, mainjar and logs folders respectively
RUN mkdir /etc/opentok/; mkdir /usr/local/opentok; mkdir /var/log/opentok
RUN chmod 0775 /etc/opentok; chmod 0775 /usr/local/opentok; chmod 0775 /var/log/opentok/

ENV configfolder /etc/opentok/
ENV mainclass com.opentok.raven.Boot
ENV mainjar /usr/local/opentok/raven-assembly-0.1.jar
ENV cp $configfolder:$mainjar
#ENV javaoptions=''

ADD target/scala-2.11/raven-assembly-0.1.jar /usr/local/opentok/raven-assembly-0.1.jar

CMD java -cp $cp $mainclass
