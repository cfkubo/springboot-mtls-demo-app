# Spring Boot mTLS Example

Github Pages: [https://site.arullab.com/spring-boot-mtls-test/](https://site.arullab.com/spring-boot-mtls-test/)

This repository contains two Spring Boot projects demonstrating mutual TLS (mTLS) communication:

- **spring-boot-mtls-server-test**: A REST API server configured to require mTLS.
- **spring-boot-mtls-test**: A client application that connects to the server using mTLS.

---

## Table of Contents

- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Generating Certificates and Keys](#generating-certificates-and-keys)
- [Configuration](#configuration)
- [Building the Projects](#building-the-projects)
- [Running the Applications](#running-the-applications)
- [Validating mTLS Communication](#validating-mtls-communication)
- [Troubleshooting](#troubleshooting)

---

## Project Structure

```
spring-boot-mtls-server-test/
    └── REST API server (requires mTLS)
spring-boot-mtls-test/
    └── Client application (connects using mTLS)
```

---

## Requirements

- Java 11 or higher
- Maven 3.x
- OpenSSL (for certificate generation)

---

## Generating Certificates and Keys

You need a CA, a server certificate, and a client certificate. The following steps use OpenSSL.

### 1. Generate the CA Certificate

```sh
# Generate CA private key
openssl genrsa -out ca.key 2048

# Generate CA certificate
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt \
    -subj "/C=BR/ST=SP/L=SP/O=VNGR/OU=VNGR/CN=VNGR-CA"
```

### 2. Generate Server Certificate

```sh
# Generate server private key
openssl genrsa -out server.key 2048

# Generate server CSR
openssl req -new -key server.key -out server.csr \
    -subj "/C=BR/ST=SP/L=SP/O=VNGR/OU=VNGR/CN=localhost"

# Sign server CSR with CA
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out server.crt -days 365 -sha256
```

### 3. Generate Client Certificate

```sh
# Generate client private key
openssl genrsa -out client.key 2048

# Generate client CSR
openssl req -new -key client.key -out client.csr \
    -subj "/C=BR/ST=SP/L=SP/O=VNGR/OU=VNGR/CN=client"

# Sign client CSR with CA
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out client.crt -days 365 -sha256
```

### 4. Create PKCS12 Keystores

```sh
# Server keystore (contains server key and cert)
openssl pkcs12 -export -in server.crt -inkey server.key -out server.p12 \
    -name server -CAfile ca.crt -caname root -password pass:changeit

# Client keystore (contains client key and cert)
openssl pkcs12 -export -in client.crt -inkey client.key -out client.p12 \
    -name client -CAfile ca.crt -caname root -password pass:changeit

# Truststore (contains CA cert)
keytool -import -trustcacerts -alias root -file ca.crt -keystore truststore.p12 \
    -storetype PKCS12 -storepass changeit -noprompt
```

> **Note:** Use `changeit` as the password or update the application properties accordingly.

---

## Configuration

Place the generated keystores and truststores in the appropriate `src/main/resources` folders of each project.

### Example application.properties for Server (`spring-boot-mtls-server-test/src/main/resources/application.properties`):

```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:server.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.trust-store=classpath:truststore.p12
server.ssl.trust-store-password=changeit
server.ssl.trust-store-type=PKCS12
server.ssl.client-auth=need
```

### Example application.properties for Client (`spring-boot-mtls-test/src/main/resources/application.properties`):

```properties
client.ssl.key-store=classpath:client.p12
client.ssl.key-store-password=changeit
client.ssl.trust-store=classpath:truststore.p12
client.ssl.trust-store-password=changeit
```

---

## Building the Projects

From the root of each project, run:

```sh
./mvnw clean package
```

---

## Running the Applications

### 1. Start the Server

```sh
cd spring-boot-mtls-server-test
./mvnw spring-boot:run
```

The server will start on `https://localhost:8443`.

### 2. Start the Client

In a new terminal:

```sh
cd spring-boot-mtls-test
./mvnw spring-boot:run
```

The client will attempt to connect to the server using mTLS and log the response.

---

## Validating mTLS Communication

### Using the Client Application

The client (`spring-boot-mtls-test`) will log the response from the server. If mTLS is working, you should see a successful response in the logs.

### Using curl (Optional)

You can also test the server endpoint directly using `curl` with client certificates:

```sh
curl -v https://localhost:8443/todo \
    --key src/main/resources/client.key \
    --cert src/main/resources/client.crt \
    --cacert src/main/resources/ca.pem
```

You should receive a valid response from the server if mTLS is configured correctly.

---


# Using CredHub for Certificate Management in Spring Boot mTLS Projects

This guide explains how to update your Spring Boot mTLS projects to use [CredHub](https://docs.cloudfoundry.org/credhub/) on Cloud Foundry for storing and retrieving certificates and keys, instead of local files.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Storing Certificates in CredHub](#storing-certificates-in-credhub)
- [Updating Spring Boot Applications](#updating-spring-boot-applications)
- [Sample Code Changes](#sample-code-changes)
- [Deploying to Cloud Foundry](#deploying-to-cloud-foundry)
- [Validating mTLS Communication](#validating-mtls-communication)
- [Troubleshooting](#troubleshooting)

---

## Overview

Instead of storing your keystores and truststores as files, you will:
- Store them as [CredHub](https://docs.cloudfoundry.org/credhub/) credentials (as binary or certificate types).
- Update your Spring Boot applications to fetch these credentials at runtime.
- Use the [Spring CredHub](https://docs.spring.io/spring-credhub/docs/current/reference/html/) integration to access secrets.

---

## Prerequisites

- Cloud Foundry CLI (`cf`)
- CredHub CLI (`credhub`)
- Access to a Cloud Foundry environment with CredHub enabled
- Java 11+, Maven 3.x
- Your generated `server.p12`, `client.p12`, and `truststore.p12` files

---

## Storing Certificates in CredHub

### 1. Login to CredHub

```sh
cf login
cf target -o <org> -s <space>
cf ssh <app-with-credhub-access>
# OR, if you have direct access:
credhub api <CREDHUB_API_URL> --ca-cert <CA_CERT>
credhub login
```

### 2. Store Keystores and Truststores as Binary

```sh
credhub set -n /mtls/server-keystore -t binary -v "$(base64 < server.p12)"
credhub set -n /mtls/client-keystore -t binary -v "$(base64 < client.p12)"
credhub set -n /mtls/truststore -t binary -v "$(base64 < truststore.p12)"
```

> **Note:** You can use any path/naming convention you prefer.

---

## Updating Spring Boot Applications

### 1. Add Spring CredHub Dependency

Add to both `pom.xml` files:

```xml
<!-- ...existing code... -->
<dependency>
    <groupId>org.springframework.credhub</groupId>
    <artifactId>spring-credhub-starter</artifactId>
    <version>3.0.0</version>
</dependency>
<!-- ...existing code... -->
```

### 2. Update `application.properties`

Replace keystore/truststore file paths with CredHub references:

```properties
# Example for server
server.ssl.key-store=credhub:/mtls/server-keystore
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.trust-store=credhub:/mtls/truststore
server.ssl.trust-store-password=changeit
server.ssl.trust-store-type=PKCS12
server.ssl.client-auth=need
```

```properties
# Example for client
client.ssl.key-store=credhub:/mtls/client-keystore
client.ssl.key-store-password=changeit
client.ssl.trust-store=credhub:/mtls/truststore
client.ssl.trust-store-password=changeit
```

### 3. Enable CredHub Integration

Add the following to your `application.properties` or `application.yml`:

```properties
spring.credhub.url=https://<CREDHUB_API_URL>
spring.credhub.oauth2.client-id=credhub-admin
spring.credhub.oauth2.client-secret=<CLIENT_SECRET>
spring.credhub.oauth2.access-token-uri=https://<UAA_URL>/oauth/token
spring.credhub.oauth2.grant-type=client_credentials
```

> Adjust these values according to your Cloud Foundry environment and credentials.

---

## Sample Code Changes

If you want to load the keystore/truststore from CredHub at runtime, you can use the following approach in your `@Configuration` class:

```java
// filepath: src/main/java/com/example/config/SslConfig.java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.credhub.core.CredHubTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.Base64;

@Configuration
public class SslConfig {

    private final CredHubTemplate credHubTemplate;

    public SslConfig(CredHubTemplate credHubTemplate) {
        this.credHubTemplate = credHubTemplate;
    }

    @Value("${server.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${server.ssl.trust-store-password}")
    private String trustStorePassword;

    @Bean
    public KeyStore keyStore() throws Exception {
        String base64 = credHubTemplate.getByName("/mtls/server-keystore", String.class).getValue();
        byte[] decoded = Base64.getDecoder().decode(base64);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayResource(decoded).getInputStream(), keyStorePassword.toCharArray());
        return ks;
    }

    @Bean
    public KeyStore trustStore() throws Exception {
        String base64 = credHubTemplate.getByName("/mtls/truststore", String.class).getValue();
        byte[] decoded = Base64.getDecoder().decode(base64);
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(new ByteArrayResource(decoded).getInputStream(), trustStorePassword.toCharArray());
        return ts;
    }
}
```

> Adjust the bean usage according to your application's SSL context setup.

---

## Deploying to Cloud Foundry

1. Push your apps as usual:
    ```sh
    cf push spring-boot-mtls-server-test
    cf push spring-boot-mtls-test
    ```
2. Ensure your app has the correct permissions to access CredHub (via service bindings or UAA scopes).

---

## Validating mTLS Communication

- The client app should connect to the server using certificates fetched from CredHub.
- You can still use `curl` as before, but the apps themselves will no longer require local keystore/truststore files.

---

## Troubleshooting

- **Permission Denied:** Ensure your app/service account has access to the CredHub paths.
- **SSL Errors:** Double-check that the keystore/truststore contents in CredHub are correct and match the passwords.
- **CredHub Integration:** Review [Spring CredHub documentation](https://docs.spring.io/spring-credhub/docs/current/reference/html/) for advanced usage.

---

## References

- [Spring CredHub Reference](https://docs.spring.io/spring-credhub/docs/current/reference/html/)
- [Cloud Foundry CredHub](https://docs.cloudfoundry.org/credhub/)




## Troubleshooting

- **SSL Handshake Errors:** Ensure the keystore/truststore passwords and file paths are correct.
- **Certificate Not Trusted:** Make sure the CA certificate is present in the truststore.
- **Port Conflicts:** Ensure port 8443 is not in use by another process.

---

## License

See [LICENSE](LICENSE) for details.

---

## Contact

For questions or support, please contact the repository maintainer.# springboot-mtls-demo-app
# springboot-mtls-demo-app
