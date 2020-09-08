mkdir standalone
cd standalone
curl -O http://packages.confluent.io/archive/5.5/confluent-community-5.5.1-2.12.tar.gz
tar xf confluent-community-5.5.1-2.12.tar.gz

wget -nv https://github.com/ElisaOyj/kafka-connect-snmp/releases/download/v0.0.5/kafka-connect-snmp-v0.0.5.tgz
tar xf kafka-connect-snmp-v0.0.5.tgz

