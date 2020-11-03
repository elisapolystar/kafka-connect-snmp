#!/bin/bash

# default port for distributed connect server REST API listener
CONNECT_REST_PORT=8083
CONNECT_REST_HOST=localhost

#script to configure kafka connect with plugins
url=http://$CONNECT_REST_HOST:$CONNECT_REST_PORT/connectors
curl_command="curl -s -o /dev/null -w %{http_code} $url"
sleep_second=15
sleep_second_counter=0
max_seconds_to_wait=300

echo "Waiting for Kafka Connect to start listening"
echo "HOST: $CONNECT_REST_HOST , PORT: $CONNECT_REST_PORT"

while [[ $(eval $curl_command) -ne 200 && $sleep_second_counter -lt $max_seconds_to_wait ]]
do
    echo " Kafka Connect listener HTTP state: " $(eval $curl_command) " (waiting for 200) $sleep_second_counter"
    sleep $sleep_second
    ((sleep_second_counter+=$sleep_second))
done

if [ $(eval $curl_command) -eq 200 ]
then
    echo "Setting up plugin configuration"
    curl -s -X POST -H "Content-Type: application/json" --data '{"name":"snmp-producer","config":{"listen.port":"162","topic":"fm_snmp","connector.class":"com.github.jcustenborder.kafka.connect.snmp.SnmpTrapSourceConnector","key.converter.schemas.enabled":"false","value.converter.schemas.enabled":"false"}}' $url
    echo -e "\nFinished setting up!"
else
    echo "Failed to connect to kafka-connect!"
fi
