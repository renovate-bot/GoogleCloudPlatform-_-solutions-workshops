# Copyright 2023 Google LLC
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

PKG := github.com/googlecloudplatform/solutions-workshops/grpc-xds/greeter-go

# Set global Skaffold options
export SKAFFOLD_BUILD_CONCURRENCY:=0
export SKAFFOLD_CLEANUP:=false
export SKAFFOLD_DETECT_MINIKUBE:=false
export SKAFFOLD_INTERACTIVE:=false
export SKAFFOLD_SKIP_TESTS:=true
export SKAFFOLD_UPDATE_CHECK:=false

.PHONY: all
all: clean proto format lint test

# run builds and deploys both greeter-intermediary and greeter-leaf and sets up port forwarding.
.PHONY: run
run:
	skaffold run --port-forward=user

# run-no-port-forward builds and deploys both greeter-intermediary and greeter-leaf.
.PHONY: run-no-port-forward
run-no-port-forward:
	skaffold run --port-forward=off

# run-no-xds builds and deploys both greeter-intermediary and greeter-leaf with no xDS configuration.
.PHONY: run-no-xds
run-no-xds:
	skaffold run --port-forward=user --profile=no-xds

# debug builds and deploys both greeter-intermediary and greeter-leaf, and sets up remote debugging.
.PHONY: debug
debug:
	skaffold debug --port-forward=user

# dev builds and deploys both greeter-intermediary and greeter-leaf, and sets up file watching for
# automatic image rebuilds and redeployments on source code changes.
.PHONY: dev
dev:
	skaffold dev

# tail the logs of both greeter-intermediary and greeter-leaf.
.PHONY: tail
tail:
	kubectl logs --all-containers --follow --namespace=xds --selector="app.kubernetes.io/name in (greeter-intermediary, greeter-leaf)"

# tail-intermediary tails only the greeter-intermediary logs (all containers).
.PHONY: tail-intermediary
tail-intermediary:
	kubectl logs --all-containers --follow --namespace=xds deployment/greeter-intermediary

# tail-intermediary tails only the greeter-leaf logs (all containers).
.PHONY: tail-leaf
tail-leaf:
	kubectl logs --all-containers --follow --namespace=xds deployment/greeter-leaf

# xds-status-intermediary shows the current xDS resources ACKed by greeter-intermediary
.PHONY: xds-status-intermediary
xds-status-intermediary:
	grpcdebug localhost:50055 xds status

# xds-status-leaf shows the current xDS resources ACKed by greeter-leaf
.PHONY: xds-status-leaf
xds-status-leaf:
	grpcdebug localhost:50057 xds status

# xds-config-intermediary dumps the current xDS configuration of greeter-intermediary
.PHONY: xds-config-intermediary
xds-config-intermediary:
	grpcdebug localhost:50055 xds config | yq --input-format=json --prettyPrint

# xds-config-leaf dumps the current xDS configuration of greeter-leaf
.PHONY: xds-config-leaf
xds-config-leaf:
	grpcdebug localhost:50057 xds config | yq --input-format=json --prettyPrint

# delete the greeter-intermediary and greeter-leaf Deployment and Service resources from the k8s cluster.
.PHONY: delete
delete:
	kubectl delete --ignore-not-found --namespace=xds deployment greeter-intermediary greeter-leaf
	kubectl delete --ignore-not-found --namespace=xds service greeter-intermediary greeter-leaf

.PHONY: build
build:
	CGO_ENABLED=0 go build $(PKG)

.PHONY: clean
clean:
	go clean -i $(PKG)/...

.PHONY: deps
deps:
	go mod download

.PHONY: format
format:
	goimports -e -l -local $(PKG) -w .

.PHONY: image
image:
	skaffold build

.PHONY: imageko
imageko:
	ko build --base-import-paths --local --sbom=none

.PHONY: lint
lint:
	golangci-lint -v run

.PHONY: manifests
manifests:
	skaffold render --digest-source=local --loud=false

.PHONY: proto
proto:
	go generate $(PKG)/...

.PHONY: test
test:
	go test -count 1 -race -timeout 3m -v $(PKG)/...

.PHONY: updatedeps
updatedeps:
	go get -d -t -u $(PKG)/...
