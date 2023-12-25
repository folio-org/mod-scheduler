package org.folio.scheduler.integration.securestore.impl;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.securestore.SecureStore;
import org.folio.scheduler.integration.securestore.configuration.properties.VaultConfigProperties;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;

@Log4j2
public final class VaultStore implements SecureStore {

  private Vault vault;
  private String secretRoot;

  private VaultStore(VaultConfigProperties properties) {
    try {
      this.vault = createVault(properties);
      this.secretRoot = properties.getSecretRoot();
    } catch (Exception e) {
      log.error("Failed to initialize: ", e);
      throw new IllegalStateException(format("Cannot initialize vault: message = %s", e.getMessage()), e);
    }
  }

  @Override
  public String get(String key) {
    log.debug("Getting value for key: {}", key);

    var keyParts = getKeyParts(key);

    var path = getKeyPath(keyParts);
    var secretName = keyParts[keyParts.length - 1];

    return getValue(path, secretName);
  }

  @Override
  public void set(String key, String value) {
    log.debug("Setting value for key: {}", key);

    var keyParts = getKeyParts(key);

    var path = getKeyPath(keyParts);
    var secretName = keyParts[keyParts.length - 1];

    setValue(path, secretName, value);
  }

  public static VaultStore create(VaultConfigProperties properties) {
    return new VaultStore(properties);
  }

  private static Vault createVault(VaultConfigProperties vaultConfigProperties) throws VaultException {
    var config = new VaultConfig()
      .address(vaultConfigProperties.getAddress())
      .token(vaultConfigProperties.getToken());
    if (nonNull(vaultConfigProperties.getEnableSsl()) && vaultConfigProperties.getEnableSsl()) {
      var sslConfig = createSslConfig(vaultConfigProperties);
      config.sslConfig(sslConfig);
    }
    return new Vault(config.build());
  }

  private static SslConfig createSslConfig(VaultConfigProperties properties) throws VaultException {
    var sslConfig = new SslConfig();
    if (nonNull(properties.getPemFilePath())) {
      sslConfig.clientKeyPemFile(new File(properties.getPemFilePath()));
    }
    if (nonNull(properties.getTruststoreFilePath())) {
      sslConfig.trustStoreFile(new File(properties.getTruststoreFilePath()));
    }
    if (nonNull(properties.getKeystoreFilePath())) {
      sslConfig.keyStoreFile(new File(properties.getKeystoreFilePath()), properties.getKeystorePassword());
    }
    return sslConfig;
  }

  private String getValue(String path, String secretName) {
    log.debug("Retrieving secret for: path = {}, secret name = {}", path, secretName);
    try {
      var secretPath = addRootPath(path);

      var ret = vault.logical()
        .read(secretPath)
        .getData()
        .get(secretName);
      if (ret == null) {
        throw new NotFoundException(format("Attribute: %s not set for %s", secretName, path));
      }
      return ret;
    } catch (VaultException e) {
      throw new NotFoundException(e);
    }
  }

  private void setValue(String path, String secretName, String value) {
    log.debug("Setting secret for: path = {}, secret name = {}", path, secretName);
    try {
      var secretPath = addRootPath(path);
      mergeSecrets(secretPath, secretName, value);
    } catch (VaultException e) {
      throw new RuntimeException("Failed to save secret for " + secretName, e);
    }
  }

  private void mergeSecrets(String secretPath, String secretName, String value) throws VaultException {
    var existingSecrets = vault.logical().read(secretPath).getData();
    var updatedSecrets = new HashMap<String, Object>(existingSecrets);
    updatedSecrets.put(secretName, value);
    vault.logical().write(secretPath, updatedSecrets);
  }

  private static String[] getKeyParts(String key) {
    validateKey(key);

    var keyParts = key.split("_");
    if (keyParts.length < 2) {
      throw new IllegalArgumentException("Key should consist of at least two parts separated by '_'");
    }
    return keyParts;
  }

  private static void validateKey(String key) {
    if (isNull(key) || key.length() == 0) {
      throw new IllegalArgumentException("Key is empty");
    }
  }

  private static String getKeyPath(String[] keyParts) {
    return String.join("/", Arrays.copyOf(keyParts, keyParts.length - 1));
  }

  private String addRootPath(String path) {
    return (secretRoot != null && !secretRoot.isEmpty())
      ? secretRoot + "/" + path
      : path;
  }
}
