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

package com.google.examples.xds.greeter.server;

import com.google.examples.xds.greeter.config.ServerConfig;
import com.google.examples.xds.greeter.interceptors.LoggingServerInterceptor;
import com.google.examples.xds.greeter.service.GreeterClient;
import com.google.examples.xds.greeter.service.GreeterIntermediary;
import com.google.examples.xds.greeter.service.GreeterLeaf;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.ChannelzService;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.services.AdminInterface;
import io.grpc.xds.XdsServerBuilder;
import io.grpc.xds.XdsServerCredentials;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A server that hosts the <code>helloworld.Greeter</code> service, and infrastructure services like
 * health, reflection, channelz and client status discovery service (CSDS).
 */
public class Server {

  private static final Logger LOG = LoggerFactory.getLogger(Server.class);

  /**
   * Channelz page size.
   *
   * @see io.grpc.services.AdminInterface#DEFAULT_CHANNELZ_MAX_PAGE_SIZE
   */
  private static final int DEFAULT_CHANNELZ_MAX_PAGE_SIZE = 100;

  /** Runs the server. */
  public void run(ServerConfig config) throws Exception {
    int servingPort = config.servingPort();
    boolean useXds = config.useXds();
    var health = new HealthStatusManager();
    var serverBuilder = createServerBuilder(servingPort, useXds);
    // Start separate server on different port for health checks and admin services.
    var healthServerBuilder = createServerBuilder(config.healthPort(), false);
    serverBuilder
        .addService(ProtoReflectionService.newInstance())
        .addService(health.getHealthService());
    healthServerBuilder
        .addService(ProtoReflectionService.newInstance())
        .addService(health.getHealthService());
    if (useXds) {
      // Channelz and CSDS for xDS server
      serverBuilder.addServices(AdminInterface.getStandardServices());
      healthServerBuilder.addServices(AdminInterface.getStandardServices());
    } else {
      // No CSDS service for non-xDS server
      serverBuilder.addService(
          ChannelzService.newInstance(DEFAULT_CHANNELZ_MAX_PAGE_SIZE).bindService());
      // No CSDS service for non-xDS server
      healthServerBuilder.addService(
          ChannelzService.newInstance(DEFAULT_CHANNELZ_MAX_PAGE_SIZE).bindService());
    }

    String greeterName = config.greeterName();
    ServerServiceDefinition greeterServiceDefinition;
    String nextHop = config.nextHop();
    if (nextHop.isBlank()) {
      LOG.info("Adding leaf Greeter service, as NEXT_HOP is not provided.");
      greeterServiceDefinition = new GreeterLeaf(greeterName).bindService();
    } else {
      LOG.info("Adding intermediary Greeter service, NEXT_HOP is {}.", nextHop);
      var client = new GreeterClient(nextHop, useXds);
      greeterServiceDefinition = new GreeterIntermediary(greeterName, client).bindService();
    }
    var greeterServiceWithLogging =
        ServerInterceptors.intercept(greeterServiceDefinition, new LoggingServerInterceptor());
    serverBuilder.addService(greeterServiceWithLogging);

    var server = serverBuilder.build().start();
    LOG.info("Greeter service with nextHop={} listening on port {}.", nextHop, servingPort);

    var healthServer = healthServerBuilder.build().start();
    addServerShutdownHook(server, healthServer, health);
    health.setStatus("", ServingStatus.SERVING);
    health.setStatus(
        greeterServiceDefinition.getServiceDescriptor().getName(), ServingStatus.SERVING);
    server.awaitTermination();
  }

  /** Creates a builder for either an xDS server or a plain old gRPC server. */
  private ServerBuilder<?> createServerBuilder(int port, boolean useXds) {
    if (!useXds) {
      LOG.info("Creating a non-xDS managed server.");
      return Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create());
    }
    LOG.info("Creating an xDS-managed server.");
    // The xDS credentials use the security configured by the xDS server when available. When xDS
    // is not used or when xDS does not provide security configuration, the xDS credentials fall
    // back to other credentials (in this case, InsecureServerCredentials).
    var serverCredentials = XdsServerCredentials.create(InsecureServerCredentials.create());
    return XdsServerBuilder.forPort(port, serverCredentials);
  }

  private void addServerShutdownHook(
      io.grpc.Server server, io.grpc.Server healthServer, HealthStatusManager health) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  // Mark all services as NOT_SERVING.
                  health.enterTerminalState();
                  // Start graceful shutdown
                  server.shutdown();
                  try {
                    // Wait for RPCs to complete processing
                    if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                      // That was plenty of time. Let's cancel the remaining RPCs.
                      server.shutdownNow();
                      // shutdownNow isn't instantaneous, so give a bit of time to clean resources
                      // up gracefully. Normally this will be well under a second.
                      server.awaitTermination(2, TimeUnit.SECONDS);
                    }
                    healthServer.shutdownNow();
                    healthServer.awaitTermination(2, TimeUnit.SECONDS);
                  } catch (InterruptedException ex) {
                    healthServer.shutdownNow();
                    server.shutdownNow();
                  }
                }));
  }
}