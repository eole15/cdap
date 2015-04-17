#!/usr/bin/env bash

# Copyright © 2014-2015 Cask Data, Inc.
# 
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
# 
# http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

source ../_common/common-build.sh

CHECK_INCLUDES=$TRUE

function pandoc_includes() {
  # Uses pandoc to translate the README markdown files to rst in the target directory
  INCLUDES_DIR=$1
  
  if [ $TEST_INCLUDES == $TEST_INCLUDES_LOCAL ]; then
    # For the local to work, must have the local sources synced to the correct branch as the remote.
    MD_CLIENTS="../../../cdap-clients"
    MD_INGEST="../../../cdap-ingest"
  elif [ $TEST_INCLUDES == $TEST_INCLUDES_REMOTE ]; then
    # https://raw.githubusercontent.com/caskdata/cdap-clients/develop/cdap-authentication-clients/java/README.md
    # https://raw.githubusercontent.com/caskdata/cdap-ingest/release/1.0.0/cdap-file-drop-zone/README.md
    GITHUB_URL="https://raw.githubusercontent.com/caskdata"
    MD_CLIENTS="$GITHUB_URL/cdap-clients/release/1.1.0"
    MD_INGEST="$GITHUB_URL/cdap-ingest/release/1.0.0"
  fi

  local java_client_working="$INCLUDES_DIR/cdap-authentication-clients-java_working.rst"
  local java_client="$INCLUDES_DIR/cdap-authentication-clients-java.rst"

  if [ "x$TEST_INCLUDES" == "x$TEST_INCLUDES_LOCAL" -o "x$TEST_INCLUDES" == "x$TEST_INCLUDES_REMOTE" ]; then
    echo "Using $TEST_INCLUDES includes..."

    pandoc -t rst -r markdown $MD_CLIENTS/cdap-authentication-clients/java/README.md    -o $java_client_working
    pandoc -t rst -r markdown $MD_CLIENTS/cdap-authentication-clients/python/README.md  -o $INCLUDES_DIR/cdap-authentication-clients-python.rst

    pandoc -t rst -r markdown $MD_INGEST/cdap-file-drop-zone/README.md        -o $INCLUDES_DIR/cdap-file-drop-zone.rst
    pandoc -t rst -r markdown $MD_INGEST/cdap-file-tailer/README.md           -o $INCLUDES_DIR/cdap-file-tailer.rst
    pandoc -t rst -r markdown $MD_INGEST/cdap-flume/README.md                 -o $INCLUDES_DIR/cdap-flume.rst
    pandoc -t rst -r markdown $MD_INGEST/cdap-stream-clients/java/README.md   -o $INCLUDES_DIR/cdap-stream-clients-java.rst
    pandoc -t rst -r markdown $MD_INGEST/cdap-stream-clients/python/README.md -o $INCLUDES_DIR/cdap-stream-clients-python.rst
  else
    echo -e "$WARNING Not testing includes: $TEST_INCLUDES includes..."
    local java_client_source="$SCRIPT_PATH/$SOURCE/$INCLUDES/cdap-authentication-clients-java.rst"
    cp -f $java_client_source $java_client_working
  fi
  
  # Fix version(s)
  local release_version="1.1.0-SNAPSHOT" # Version to be written into file
  rewrite $java_client_working $java_client "{version}" $release_version
}

function test_an_include() {
  # Tests a file and checks that it hasn't changed.
  # Uses md5 hashes to monitor if any files have changed.
  local new_md5_hash
  local md5_hash=${1}
  local file_name=${2}
  
  local includes_dir=${SCRIPT_PATH}/${BUILD}/${INCLUDES}
  local target=${includes_dir}/${file_name}

  if [[ "x${OSTYPE}" == "xdarwin"* ]]; then
    new_md5_hash=`md5 -q ${target}`
  else
    new_md5_hash=`md5sum ${target} | awk '{print ${1}}'`
  fi
  
  if [ "x${md5_hash}" != "x${new_md5_hash}" ]; then
    echo -e "${WARNING} MD5 Hash for ${file_name} has changed! Compare files and update hash!"  
    echo "Old md5_hash: ${md5_hash} New md5_hash: ${new_md5_hash}"
  else
    echo "MD5 Hash for ${file_name} matches"  
  fi
}

function test_includes() {
  echo "Testing includes downloaded and converted from GitHub..."
  test_an_include b1f5dbf0f08f68d1cee8355d4d627263 cdap-authentication-clients-java.rst
  test_an_include 7ad6fbad1c5b1309e0667c2ad9ab537f cdap-authentication-clients-python.rst
  test_an_include 5923713fdb6e4d542fb6861a7440c0ea cdap-file-drop-zone.rst
  test_an_include 1443cc94b19229a45ceb163a04894839 cdap-file-tailer.rst
  test_an_include 5dad2bde949162563d504d0d52daa474 cdap-flume.rst
  test_an_include 02d61be34767136301242d39ef9c3940 cdap-stream-clients-java.rst
  test_an_include 971837a0693734ba33dee581a4af26c4 cdap-stream-clients-python.rst
}

run_command $1
