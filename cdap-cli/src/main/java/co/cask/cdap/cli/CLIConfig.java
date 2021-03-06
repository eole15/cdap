/*
 * Copyright © 2014-2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.cli;

import co.cask.cdap.cli.command.system.VersionCommand;
import co.cask.cdap.cli.util.FilePathResolver;
import co.cask.cdap.cli.util.table.AltStyleTableRenderer;
import co.cask.cdap.cli.util.table.TableRenderer;
import co.cask.cdap.cli.util.table.TableRendererConfig;
import co.cask.cdap.client.MetaClient;
import co.cask.cdap.client.config.ClientConfig;
import co.cask.cdap.client.config.ConnectionConfig;
import co.cask.cdap.common.exception.UnauthorizedException;
import co.cask.cdap.proto.Id;
import co.cask.cdap.security.authentication.client.AccessToken;
import co.cask.cdap.security.authentication.client.AuthenticationClient;
import co.cask.cdap.security.authentication.client.Credential;
import co.cask.cdap.security.authentication.client.basic.BasicAuthenticationClient;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import jline.TerminalFactory;
import jline.console.ConsoleReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nullable;

/**
 * Configuration for the CDAP CLI.
 */
public class CLIConfig implements TableRendererConfig {

  public static final String ENV_ACCESSTOKEN = "ACCESS_TOKEN";

  private static final int DEFAULT_LINE_WIDTH = 80;
  private static final int MIN_LINE_WIDTH = 40;

  private final ClientConfig clientConfig;
  private final FilePathResolver resolver;
  private final String version;
  private final PrintStream output;

  private TableRenderer tableRenderer;
  private List<ConnectionChangeListener> connectionChangeListeners;
  private Supplier<Integer> lineWidthSupplier = new Supplier<Integer>() {
    @Override
    public Integer get() {
      try {
        return TerminalFactory.get().getWidth();
      } catch (Exception e) {
        return DEFAULT_LINE_WIDTH;
      }
    }
  };

  /**
   * @param clientConfig client configuration
   */
  public CLIConfig(ClientConfig clientConfig, PrintStream output, TableRenderer tableRenderer) {
    this.clientConfig = clientConfig;
    this.output = output;
    this.tableRenderer = tableRenderer;
    this.resolver = new FilePathResolver();
    this.version = tryGetVersion();
    this.connectionChangeListeners = Lists.newArrayList();
  }

  public CLIConfig() {
    this(ClientConfig.builder().build(), System.out, new AltStyleTableRenderer());
  }

  public PrintStream getOutput() {
    return output;
  }

  public TableRenderer getTableRenderer() {
    return tableRenderer;
  }

  public Id.Namespace getCurrentNamespace() {
    return clientConfig.getNamespace();
  }

  public void setTableRenderer(TableRenderer tableRenderer) {
    this.tableRenderer = tableRenderer;
  }

  public void setConnectionConfig(@Nullable ConnectionConfig connectionConfig) {
    clientConfig.setConnectionConfig(connectionConfig);
    notifyConnectionChanged();
  }

  public void tryConnect(ConnectionConfig connectionConfig, PrintStream output, boolean debug) throws Exception {
    try {
      AccessToken accessToken = acquireAccessToken(clientConfig, connectionConfig, output, debug);
      checkConnection(clientConfig, connectionConfig, accessToken);
      setConnectionConfig(connectionConfig);
      output.printf("Successfully connected CDAP instance at %s", connectionConfig.getURI().toString());
      output.println();
    } catch (IOException e) {
      throw new IOException(String.format("CDAP instance at '%s' could not be reached: %s",
                                          connectionConfig.getURI().toString(), e.getMessage()), e);
    }
  }

  public void updateAccessToken(PrintStream output) throws IOException {
    AccessToken newAccessToken = getNewAccessToken(clientConfig.getConnectionConfig(), output, false);
    clientConfig.setAccessToken(newAccessToken);
  }

  private void checkConnection(ClientConfig baseClientConfig,
                               ConnectionConfig connectionInfo,
                               AccessToken accessToken) throws IOException, UnauthorizedException {
    ClientConfig clientConfig = new ClientConfig.Builder(baseClientConfig)
      .setConnectionConfig(connectionInfo)
      .setAccessToken(accessToken)
      .build();
    MetaClient metaClient = new MetaClient(clientConfig);
    metaClient.ping();
  }

  private boolean isAuthenticationEnabled(ConnectionConfig connectionInfo) throws IOException {
    return getAuthenticationClient(connectionInfo).isAuthEnabled();
  }

  private AccessToken acquireAccessToken(ClientConfig clientConfig, ConnectionConfig connectionInfo,
                                         PrintStream output, boolean debug) throws IOException {

    if (!isAuthenticationEnabled(connectionInfo)) {
      return null;
    }

    try {
      AccessToken savedAccessToken = getSavedAccessToken(connectionInfo.getHostname());
      checkConnection(clientConfig, connectionInfo, savedAccessToken);
      return savedAccessToken;
    } catch (UnauthorizedException ignored) {
      // access token invalid - fall through to try acquiring token manually
    }

    return getNewAccessToken(connectionInfo, output, debug);
  }

  private AccessToken getNewAccessToken(ConnectionConfig connectionInfo, PrintStream output,
                                        boolean debug) throws IOException {

    AuthenticationClient authenticationClient = getAuthenticationClient(connectionInfo);

    Properties properties = new Properties();
    properties.put(BasicAuthenticationClient.VERIFY_SSL_CERT_PROP_NAME,
                   String.valueOf(clientConfig.isVerifySSLCert()));

    // obtain new access token via manual user input
    output.printf("Authentication is enabled in the CDAP instance: %s.\n", connectionInfo.getHostname());
    ConsoleReader reader = new ConsoleReader();
    for (Credential credential : authenticationClient.getRequiredCredentials()) {
      String prompt = "Please, specify " + credential.getDescription() + "> ";
      String credentialValue;
      if (credential.isSecret()) {
        credentialValue = reader.readLine(prompt, '*');
      } else {
        credentialValue = reader.readLine(prompt);
      }
      properties.put(credential.getName(), credentialValue);
    }

    authenticationClient.configure(properties);
    AccessToken accessToken = authenticationClient.getAccessToken();

    if (accessToken != null) {
      if (saveAccessToken(accessToken, connectionInfo.getHostname()) && debug) {
        output.printf("Saved access token to %s\n", getAccessTokenFile(connectionInfo.getHostname()).getAbsolutePath());
      }
    }

    return accessToken;
  }

  private AuthenticationClient getAuthenticationClient(ConnectionConfig connectionInfo) {
    AuthenticationClient authenticationClient = new BasicAuthenticationClient();
    authenticationClient.setConnectionInfo(connectionInfo.getHostname(), connectionInfo.getPort(),
                                           connectionInfo.isSSLEnabled());
    return authenticationClient;
  }

  private AccessToken getSavedAccessToken(String hostname) {
    File file = getAccessTokenFile(hostname);
    if (file.exists() && file.canRead()) {
      try {
        String tokenString = Joiner.on("").join(Files.readLines(file, Charsets.UTF_8));
        return new AccessToken(tokenString, -1L, null);
      } catch (IOException ignored) {
        // Fall through
      }
    }
    return null;
  }

  private boolean saveAccessToken(AccessToken accessToken, String hostname) {
    File accessTokenFile = getAccessTokenFile(hostname);

    try {
      accessTokenFile.createNewFile();
      Files.write(accessToken.getValue(), accessTokenFile, Charsets.UTF_8);
      return true;
    } catch (IOException ignored) {
      // NO-OP
    }

    return false;
  }

  private File getAccessTokenFile(String hostname) {
    String accessTokenEnv = System.getenv(CLIConfig.ENV_ACCESSTOKEN);
    if (accessTokenEnv != null) {
      return resolver.resolvePathToFile(accessTokenEnv);
    }

    return resolver.resolvePathToFile("~/.cdap.accesstoken." + hostname);
  }

  private String tryGetVersion() {
    try {
      InputSupplier<? extends InputStream> versionFileSupplier = new InputSupplier<InputStream>() {
        @Override
        public InputStream getInput() throws IOException {
          return VersionCommand.class.getClassLoader().getResourceAsStream("VERSION");
        }
      };
      return CharStreams.toString(CharStreams.newReaderSupplier(versionFileSupplier, Charsets.UTF_8));
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  public void setNamespace(Id.Namespace namespace) {
    ConnectionConfig connectionConfig = ConnectionConfig.builder(clientConfig.getConnectionConfig())
      .setNamespace(namespace)
      .build();
    this.setConnectionConfig(connectionConfig);
  }

  public ClientConfig getClientConfig() {
    return clientConfig;
  }

  public String getVersion() {
    return version;
  }

  public void addHostnameChangeListener(ConnectionChangeListener listener) {
    this.connectionChangeListeners.add(listener);
  }

  private void notifyConnectionChanged() {
    for (ConnectionChangeListener listener : connectionChangeListeners) {
      listener.onConnectionChanged(clientConfig);
    }
  }

  public void setLineWidth(Supplier<Integer> lineWidthSupplier) {
    this.lineWidthSupplier = lineWidthSupplier;
  }

  public int getLineWidth() {
    return Math.max(MIN_LINE_WIDTH, lineWidthSupplier.get());
  }

  /**
   * Listener for hostname changes.
   */
  public interface ConnectionChangeListener {
    void onConnectionChanged(ClientConfig clientConfig);
  }
}
