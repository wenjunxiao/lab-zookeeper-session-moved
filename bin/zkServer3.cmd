@echo off
title Zookeeper3
setlocal

set ZOOCFG=%ZOOCFGDIR%\zoo3.cfg
echo on
java "%ZOO_AGENT%" "-Dzookeeper.log.dir=%ZOO_LOG_DIR%\3" "-Dzookeeper.root.logger=%ZOO_LOG4J_PROP%" -cp "%CLASSPATH%" %ZOOMAIN% "%ZOOCFG%" %*
endlocal

