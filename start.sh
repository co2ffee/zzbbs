#!/bin/bash -e
java -jar -Xms256M -Xmx256M pybbs.jar --spring.profiles.active=prod > log.file 2>&1 &
