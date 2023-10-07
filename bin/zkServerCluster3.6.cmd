@echo off
title ZookeeperCluster3.6.0
setlocal

@REM zookeeper 3.6.0 resolve session moved, but not supported by elastic-job 2.1.5
set SESSION_ID_MASK=0x00ffffffff000000L
set ZK_VERSION=zookeeper-3.6.0
set ZK_PKG_NAME=apache-%ZK_VERSION%-bin

call "%~dp0zkServerCluster.cmd" || pause

endlocal