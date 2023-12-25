package org.folio.scheduler.integration.securestore.impl;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.nonNull;
import static software.amazon.awssdk.services.ssm.model.ParameterType.SECURE_STRING;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.securestore.SecureStore;
import org.folio.scheduler.integration.securestore.configuration.properties.AwsConfigProperties;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

@Log4j2
public final class AwsStore implements SecureStore {

  public static final String ECS_CREDENTIALS_PATH_VAR = "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI";

  private SsmClient ssmClient;

  private AwsStore(AwsConfigProperties properties) {
    ssmClient = buildClient(properties);
  }

  @Override
  public String get(String key) {
    var request = GetParameterRequest.builder().name(key).withDecryption(true).build();
    return getParameter(request);
  }

  @Override
  public void set(String key, String value) {
    var request = PutParameterRequest.builder().type(SECURE_STRING).name(key).value(value).overwrite(TRUE).build();
    ssmClient.putParameter(request);
  }

  public static AwsStore create(AwsConfigProperties properties) {
    return new AwsStore(properties);
  }

  private String getParameter(GetParameterRequest request) {
    try {
      return ssmClient.getParameter(request).parameter().value();
    } catch (Exception e) {
      throw new NotFoundException(e);
    }
  }

  private SsmClient buildClient(AwsConfigProperties properties) {
    log.info("Initializing...");
    var builder = SsmClient.builder();
    builder.region(Region.of(properties.getRegion()));
    if (nonNull(properties.getUseIam()) && properties.getUseIam()) {
      log.info("Using IAM");
    } else {
      var awsCredentialsProvider = getAwsCredentialsProvider();
      log.info("Using {}", awsCredentialsProvider.getClass().getName());
      builder.credentialsProvider(awsCredentialsProvider);
      builder.endpointOverride(endpoint(properties));
    }
    return builder.build();
  }

  private AwsCredentialsProvider getAwsCredentialsProvider() {
    AwsCredentialsProvider credProvider;
    try {
      credProvider = EnvironmentVariableCredentialsProvider.create();
      credProvider.resolveCredentials();
    } catch (Exception e) {
      try {
        credProvider = SystemPropertyCredentialsProvider.create();
        credProvider.resolveCredentials();
      } catch (Exception e2) {
        credProvider = ContainerCredentialsProvider.builder().build();
        credProvider.resolveCredentials();
      }
    }
    return credProvider;
  }

  private static URI endpoint(AwsConfigProperties properties) {
    var path = properties.getEcsCredentialsPath();
    if (path == null) {
      path = System.getenv(ECS_CREDENTIALS_PATH_VAR);
    }
    if (path == null) {
      throw SdkClientException.create("No credentials path was provided and the environment variable "
        + ECS_CREDENTIALS_PATH_VAR + " is empty");
    }

    try {
      return new URI(properties.getEcsCredentialsEndpoint() + path);
    } catch (URISyntaxException e) {
      throw SdkClientException.builder().cause(e).build();
    }
  }
}
