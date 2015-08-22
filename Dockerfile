FROM java:openjdk-8-jdk

# config, mainjar and logs folders respectively
RUN mkdir /etc/opentok/; mkdir /usr/local/opentok; mkdir /var/log/opentok
RUN chmod 0775 /etc/opentok; chmod 0775 /usr/local/opentok; chmod 0775 /var/log/opentok/

ENV configfolder /etc/opentok/
ENV mainclass com.opentok.raven.Boot
ENV mainjar /usr/local/opentok/raven-assembly.jar
ENV cp $configfolder:$mainjar
#ENV javaoptions=''

ADD target/scala-2.11/raven-assembly.jar /usr/local/opentok/

CMD java -cp $cp $mainclass
