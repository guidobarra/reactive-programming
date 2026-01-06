FROM bellsoft/liberica-openjdk-alpine:21
WORKDIR app
ADD https://github.com/vinsguru/java-reactive-programming-course/raw/master/02-external-services/external-services.jar external-services.jar
CMD java -jar external-services.jar --server.port=8080