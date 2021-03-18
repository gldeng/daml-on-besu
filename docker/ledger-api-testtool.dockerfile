# Copyright 2020 Blockchain Techology Partners
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ------------------------------------------------------------------------------
FROM ubuntu:bionic as ubuntu-base

RUN \
  apt-get update -y && \
  apt-get install -y \
  curl \
  gnupg \
  software-properties-common \
  wget

# ------------------------------------------------------------------------------
FROM ubuntu-base as ubuntu-zulu

RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 0xB1998361219BD9C9 && \
  apt-add-repository 'deb http://repos.azulsystems.com/ubuntu stable main' && \
  apt-get update -y && \
  apt-get install -y \
  zulu-11

# ------------------------------------------------------------------------------
FROM ubuntu-zulu

ARG DAML_SDK_VERSION=1.4.0
ARG DAML_REPO_VERSION=1.4.0

RUN apt-get update -y && \
  apt-get install -y \
  groff \
  python3-pip \
  tar

RUN curl -sSL https://get.daml.com/ | sh -s $DAML_SDK_VERSION && \
  mkdir -p /opt && \
  mv /root/.daml /opt/daml-sdk

ENV PATH=/opt/daml-sdk/bin:$PATH

RUN mkdir -p /opt/ledger-api-test-tool && \
  cd  /opt/ledger-api-test-tool && \
  wget -nv https://repo.maven.apache.org/maven2/com/daml/ledger-api-test-tool/${DAML_REPO_VERSION}/ledger-api-test-tool-${DAML_REPO_VERSION}.jar && \
  mv ledger-api-test-tool-${DAML_REPO_VERSION}.jar ledger-api-test-tool.jar

RUN pip3 install awscli --upgrade

WORKDIR /opt/ledger-api-test-tool

CMD ["java", "-jar","ledger-api-test-tool.jar"]
