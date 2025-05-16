Generating Certificates and Keys
Here’s a simplified process to generate all the necessary files for mTLS:

Step 1: Generate the CA Certificate
Create the CA’s Private Key:
openssl genrsa -out ca.key 2048

Step 2: Generate Server and Client Certificates
Generate Private Keys:
For the server: openssl genrsa -out server.key 2048

For the client: openssl genrsa -out client.key 2048

2. Generate CSRs:

For the server: openssl req -new -key server.key -out server.csr

For the client: openssl req -new -key client.key -out client.csr

