FROM openjdk:11
MAINTAINER Sergey Grigoriev <s.grigoriev@gmail.com>

EXPOSE 8080

ADD target/im*.jar im.jar

ENTRYPOINT ["java", "-jar", "im.jar"]