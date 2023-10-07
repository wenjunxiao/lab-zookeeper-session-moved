@echo off
title ZookeeperCluster
setlocal

cd "%~dp0"
cd ..
set LAB_HOME=%cd%\

set AGENT_FILE=%LAB_HOME%zookeeper-agent\target\zookeeper-agent-1.0-SNAPSHOT.jar
if not exist "%AGENT_FILE%" (
    cd zookeeper-agent/
    call mvn clean package
    cd "%LAB_HOME%"
)
set SESSION_ID_MASK=0xffffffffff000000L
set ZK_VERSION=zookeeper-3.4.5
set ZK_PKG_NAME=%ZK_VERSION%

@REM zookeeper 3.6.0 resolve session moved, but not supported by elastic-job 2.1.5
@REM set SESSION_ID_MASK=0x00ffffffff000000L
@REM set ZK_VERSION=zookeeper-3.6.0
@REM set ZK_PKG_NAME=apache-%ZK_VERSION%-bin

set ZK_URL=http://archive.apache.org/dist/zookeeper/%ZK_VERSION%/%ZK_PKG_NAME%.tar.gz
set LAB_DIR=%LAB_HOME%tmp
set ZK_TAR_FILE="%LAB_DIR%\%ZK_PKG_NAME%.tar.gz"
set ZOOKEEPER_DIR=%LAB_DIR%\%ZK_PKG_NAME%
REM Download zookeeper from http://archive.apache.org/dist/zookeeper/
set DOWNLOADER="%LAB_HOME%zookeeper-downloader\target\zookeeper-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar"
if not exist "%ZOOKEEPER_DIR%" (
    cd zookeeper-downloader/
    call mvn clean package
    cd "%LAB_HOME%"
    if exist "%ZK_TAR_FILE%" (
        java -jar %DOWNLOADER% %ZK_TAR_FILE% "%LAB_DIR%" || pause
    ) else (
        java -jar %DOWNLOADER% %ZK_URL% "%LAB_DIR%" || pause
    )
)

call "%ZOOKEEPER_DIR%\bin\zkEnv.cmd"
set ZOO_LOG4J_PROP=INFO,CONSOLE
set ZOOMAIN=org.apache.zookeeper.server.quorum.QuorumPeerMain
set ZOO_AGENT="-javaagent:%AGENT_FILE%"
set ZOOCFGDIR=%LAB_HOME%conf
set ZOO_LOG_DIR=%LAB_DIR%\logs
set ZOO_DATA_DIR=%LAB_DIR%\data
rmdir /S /Q %ZOO_DATA_DIR%
if not exist "%ZOO_DATA_DIR%\1\myid" (
    mkdir "%ZOO_DATA_DIR%\1" 2>nul
    >>"%ZOO_DATA_DIR%\1\myid" set /p="1" <nul
)

if not exist "%ZOO_DATA_DIR%\2\myid" (
    mkdir "%ZOO_DATA_DIR%\2"  2>nul
    >>"%ZOO_DATA_DIR%\2\myid" set /p="2" <nul
)

if not exist "%ZOO_DATA_DIR%\3\myid" (
    mkdir "%ZOO_DATA_DIR%\3"  2>nul
    >>"%ZOO_DATA_DIR%\3\myid" set /p="3" <nul
)

set SESSION_SIGNAL_DIR=%LAB_DIR%\signal
if not exist "%SESSION_SIGNAL_DIR%" (
    mkdir "%SESSION_SIGNAL_DIR%"
)

echo "start zookeeper cluster"
start "Zookeeper1" "%LAB_HOME%bin\zkServer1.cmd"
start "Zookeeper2" "%LAB_HOME%bin\zkServer2.cmd"
start "Zookeeper3" "%LAB_HOME%bin\zkServer3.cmd"

timeout /t 10
echo %time% > "%SESSION_SIGNAL_DIR%\session.txt"
endlocal