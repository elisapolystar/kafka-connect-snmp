FROM quay.io/strimzi/kafka:0.32.0-kafka-3.2.1

COPY kafka-connect-snmp/usr/share/kafka-connect/kafka-connect-snmp/ /opt/kafka/plugins/java/kafka-connect-snmp/

