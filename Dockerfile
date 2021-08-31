FROM alpine:3.14

WORKDIR /tmp/kafka-connect

COPY kafka-connect-snmp-latest.tgz kafka-connect-snmp.tgz
RUN mkdir kafka-connect-snmp
RUN tar -xf kafka-connect-snmp.tgz -C kafka-connect-snmp

FROM quay.io/strimzi/kafka:0.24.0-kafka-2.8.0

COPY --from=0 /tmp/kafka-connect/kafka-connect-snmp/usr/share/kafka-connect/kafka-connect-snmp /opt/kafka/plugins/java/kafka-connect-snmp/

