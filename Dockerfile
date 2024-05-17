FROM quay.io/strimzi/kafka:0.41.0-kafka-3.6.1

COPY kafka-connect-snmp/usr/share/kafka-connect/kafka-connect-snmp/ /opt/kafka/plugins/java/kafka-connect-snmp/

