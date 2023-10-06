@echo off
title Zookeeper2
setlocal

set ZOOCFG=%ZOOCFGDIR%\zoo2.cfg
echo on
java "%ZOO_AGENT%" "-Dzookeeper.log.dir=%ZOO_LOG_DIR%\2" "-Dzookeeper.root.logger=%ZOO_LOG4J_PROP%" -cp "%CLASSPATH%" %ZOOMAIN% "%ZOOCFG%" %*
endlocal

