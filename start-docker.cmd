


SET master=%1
SET network=--network spark_default
SET cassandra=%2


docker run --name tomee --hostname tomee --rm %network% --env HDFS_USER=%user% --env HADOOP_USER_NAME=%USERNAME% --publish 9080:9080 derrickoswald/cimapplication start-tomee %master% %cassandra%
