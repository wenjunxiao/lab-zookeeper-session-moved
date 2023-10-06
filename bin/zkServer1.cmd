@echo off
title Zookeeper1
setlocal

set ZOOCFG=%ZOOCFGDIR%\zoo1.cfg
echo on
java "%ZOO_AGENT%" "-Dzookeeper.log.dir=%ZOO_LOG_DIR%\1" "-Dzookeeper.root.logger=%ZOO_LOG4J_PROP%" -cp "%CLASSPATH%" %ZOOMAIN% "%ZOOCFG%" %*
endlocal

