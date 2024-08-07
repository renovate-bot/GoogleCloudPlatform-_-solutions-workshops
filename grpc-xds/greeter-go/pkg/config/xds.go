// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package config

import (
	"os"
)

// UseXDS determines if the gRPC server should connect with an xDS control
// plane management server for configuration.
//
// See https://github.com/grpc/grpc-go/blob/v1.57.0/internal/envconfig/xds.go
func UseXDS() bool {
	if _, exists := os.LookupEnv("GRPC_XDS_BOOTSTRAP"); exists {
		return true
	}
	if _, exists := os.LookupEnv("GRPC_XDS_BOOTSTRAP_CONFIG"); exists {
		return true
	}
	return false
}
