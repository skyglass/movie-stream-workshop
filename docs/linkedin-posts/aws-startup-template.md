# AWS Startup Template

Most startup teams do not need a massive cloud platform on day one.

They need a working product template that can be cloned, adapted, deployed, and shown to customers quickly.

This repository is exactly that kind of template.

It contains a complete business application foundation:

- Angular frontend served by Nginx
- Spring Cloud Gateway as the public API entry point
- Spring Boot backend with PostgreSQL, JPA, and Flyway migrations
- Keycloak for authentication and user management
- pgAdmin for database inspection
- Docker Compose for local and production runtime
- Terraform-based AWS infrastructure
- Caddy reverse proxy with automatic HTTPS
- Full deployment instructions for running it on AWS

The important part is not the movie-streaming domain.

The important part is the shape of the system.

Almost every early-stage business application needs the same baseline: a UI, an API, authentication, persistence, admin tooling, runtime configuration, container images, and a repeatable deployment path.

This repository packages those concerns into one practical startup template.

The AWS deployment is intentionally simple and understandable:

1. Configure `deployment/.env.prod` with AWS settings, secrets, GitHub Container Registry credentials, the application domain, and runtime values.
2. Build and publish the application images to GitHub Container Registry:

   - `movie-gateway`
   - `movies-api`
   - `movies-ui`

3. Use Terraform to create the AWS infrastructure:

   - VPC
   - public subnet
   - security group
   - EC2 instance
   - Elastic IP
   - SSH key pair
   - optional Route 53 A record
   - EBS volume attachments for persistent Postgres data

4. Point a registered domain to the EC2 Elastic IP through Route 53 or a manual DNS record.
5. Deploy the production Docker Compose stack to the EC2 instance.
6. Let Caddy terminate HTTPS automatically and route traffic:

   - `/` to the frontend
   - `/api` to the gateway
   - `/keycloak` to identity management
   - `/pgadmin` to database administration

The deployment scripts handle the operational glue:

- render production config files
- copy Compose, Caddy, Keycloak, and app config to EC2
- install Docker and Docker Compose when needed
- log in to GitHub Container Registry from EC2
- pull the published images
- start the stack with `docker compose up -d`

For updates, you publish a new image version to GHCR and redeploy the same Docker Compose stack with the new tag.

That is a very reasonable path for a startup MVP:

- local development with Docker Compose
- versioned container images in GitHub
- infrastructure described in Terraform
- one EC2 instance running the product
- persistent database storage on EBS
- HTTPS on a real registered domain
- clear instructions documented in the repository

It is not trying to be Kubernetes on day one.

It is trying to be understandable, deployable, and easy to evolve.

For a new business idea, that is often the better template.

#aws #terraform #docker #dockercompose #ec2 #github #startup #mvp #springboot #angular #keycloak #devops
