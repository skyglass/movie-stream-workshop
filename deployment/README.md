# Movie Stream AWS EC2 Deployment

This deployment runs Movie Stream on one public EC2 instance with Docker Compose and Caddy HTTPS:

- `movies-ui`
- `movie-gateway`
- `movies-api`
- PostgreSQL for the movie catalog
- PostgreSQL for Keycloak
- Keycloak
- pgAdmin
- Caddy reverse proxy with automatic Let's Encrypt certificates

Secrets and deploy-time values live in `deployment/.env.prod`, which is ignored by Git. The deployment scripts render the runtime config locally, copy it to the EC2 instance over SSH, and run Docker Compose there.

## Prerequisites

Install and configure:

- AWS CLI authenticated to the target AWS account
- Terraform `>= 1.6`
- Docker on your local machine for building and publishing images
- `ssh` and `scp`
- `envsubst`
- GitHub token with GHCR package read/write permissions
- A public domain, for example `example.com`
- Route 53 hosted zone for the domain

## 1. Create `deployment/.env.prod`

```bash
cp deployment/.env.prod.example deployment/.env.prod
```

Edit `deployment/.env.prod`.

Create a deployment-only SSH key pair in the repository-local `.ssh` folder:

```bash
mkdir -p .ssh
ssh-keygen -t ed25519 -C movie-stream-deploy -f .ssh/movie-stream -N ''
chmod 700 .ssh
chmod 600 .ssh/movie-stream
chmod 644 .ssh/movie-stream.pub
```

`.ssh/` is ignored by Git. The public key is uploaded to AWS as an EC2 key pair. The private key stays on your machine and is used by deployment scripts to connect to the EC2 instance.

Required AWS and SSH values:

```bash
AWS_REGION=eu-central-1
INFRA_NAME_PREFIX=movie-stream
AVAILABILITY_ZONE=eu-central-1a

EC2_INSTANCE_TYPE=t3.large
EC2_AMI_ARCHITECTURE=x86_64
SSH_PUBLIC_KEY_PATH=.ssh/movie-stream.pub
SSH_PRIVATE_KEY_PATH=.ssh/movie-stream
SSH_ALLOWED_CIDR=<your-public-ip>/32
```

Use your current public IPv4 address for `SSH_ALLOWED_CIDR` so only your current network can connect over SSH. On macOS, get it with:

```bash
curl -4 -s https://checkip.amazonaws.com
```

or:

```bash
dig +short myip.opendns.com @resolver1.opendns.com
```

For example, if the command prints `198.51.100.25`, set:

```bash
SSH_ALLOWED_CIDR=198.51.100.25/32
```

If your public IP changes, update `SSH_ALLOWED_CIDR` in `deployment/.env.prod` and rerun:

```bash
./deployment/start.sh infra
```

Terraform will update the EC2 security group rule. It does not need to recreate the instance just to change the allowed SSH source.

For a more stable setup, use a network with a static public egress IP, for example a VPN with a fixed public IP, and set `SSH_ALLOWED_CIDR` to that static IP with `/32`. Then connect to that VPN before running deployment scripts. Avoid `SSH_ALLOWED_CIDR=0.0.0.0/0` except as a short temporary troubleshooting step, because it exposes SSH to the internet.

Required registry values:

```bash
DOCKER_SERVER=ghcr.io
CONTAINER_REGISTRY=ghcr.io/<github-user-or-org>
GITHUB_USERNAME=<github-user>
GITHUB_TOKEN=<github-token-with-package-write>
IMAGE_VERSION=1.2.0-movie-challenge
```

Required public app values:

```bash
APP_DOMAIN=example.com
APP_BASE_URL=https://example.com
KEYCLOAK_EXTERNAL_URL=https://example.com/keycloak
```

Required database and admin values:

```bash
MOVIES_JDBC_URL=jdbc:postgresql://postgres-movies:5432/movies
MOVIES_JDBC_USERNAME=movies
MOVIES_JDBC_PASSWORD=<secure-password>

KEYCLOAK_REALM=movies
KEYCLOAK_POSTGRES_DB=keycloak
KEYCLOAK_POSTGRES_USER=keycloak
KEYCLOAK_POSTGRES_PASSWORD=<secure-password>
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<secure-password>

PGADMIN_DEFAULT_EMAIL=admin@example.com
PGADMIN_DEFAULT_PASSWORD=<secure-password>

OMDB_API_KEY=<your-omdb-api-key>
MOVIES_PER_PAGE=50
```

## 2. Prepare EBS gp3 Volumes

The two Postgres containers use existing EBS volumes mounted directly on the EC2 instance.

Create the volumes in the same Availability Zone as `AVAILABILITY_ZONE`.

In the AWS Console:

1. Open **EC2** in `eu-central-1`.
2. Go to **Volumes**.
3. Choose **Create volume**.
4. For the movie catalog database, set **Volume type** to `gp3`, **Size** to `60 GiB`, and **Availability Zone** to `AVAILABILITY_ZONE`, for example `eu-central-1a`.
5. Keep encryption enabled and add the tag `Name=movie-stream-movies-postgres`.
6. Create a second gp3 volume for Keycloak Postgres with **Size** `20 GiB`, the same **Availability Zone**, and the tag `Name=movie-stream-keycloak-postgres`.
7. Wait until both volumes are `Available`.
8. Copy both volume IDs, for example `vol-0123456789abcdef0`.

Add the volume IDs to `deployment/.env.prod`:

```bash
MOVIES_POSTGRES_VOLUME_ID=vol-0123456789abcdef0
MOVIES_POSTGRES_DEVICE_NAME=/dev/sdf
MOVIES_POSTGRES_HOST_DIR=/mnt/movie-stream/movies-postgres

KEYCLOAK_POSTGRES_VOLUME_ID=vol-0123456789abcdef1
KEYCLOAK_POSTGRES_DEVICE_NAME=/dev/sdg
KEYCLOAK_POSTGRES_HOST_DIR=/mnt/movie-stream/keycloak-postgres
```

The EC2 instance must be created in the same Availability Zone as the volumes.

## 3. Publish Docker Images

Build and publish the deployment images:

```bash
./deployment/docker-publish.sh
```

This step can be run independently whenever a new application version needs to be published. It does not create AWS infrastructure or deploy the application. The script always runs `docker build` before `docker push`, so the pushed images are built from the current source tree.

The script reads `deployment/.env.prod` and uses `IMAGE_VERSION`. With the example version, it publishes:

```text
ghcr.io/<github-user-or-org>/movie-gateway:1.2.0-movie-challenge
ghcr.io/<github-user-or-org>/movies-api:1.2.0-movie-challenge
ghcr.io/<github-user-or-org>/movies-ui:1.2.0-movie-challenge
```

The full `start.sh` workflow applies infrastructure, publishes Docker images, and deploys the EC2 Docker Compose application:

```bash
./deployment/start.sh all
```

If the images for the configured `IMAGE_VERSION` are already published, skip the Docker publish step:

```bash
./deployment/start.sh all skipDockerPublish=true
```

`skipDockerPublish` defaults to `false`; omitting it keeps the Docker publish step enabled.

## 4. Create AWS Infrastructure

Create the VPC, public subnet, EC2 instance, Elastic IP, security group, SSH key pair, and EBS attachments:

```bash
./deployment/start.sh infra
```

The script prints the Elastic IP:

```text
Elastic IP: 203.0.113.10
```

Terraform does not create or delete the database EBS volumes. It only attaches the existing volume IDs from `deployment/.env.prod` to the EC2 instance.

## 5. Configure Route 53 And HTTPS

Caddy runs on the EC2 instance and automatically obtains a Let's Encrypt certificate for `APP_DOMAIN`.

Only one certificate is needed when everything is served under one domain:

```text
https://example.com/          -> movies-ui
https://example.com/api       -> movie-gateway
https://example.com/keycloak  -> Keycloak
https://example.com/pgadmin   -> pgAdmin
```

For Let's Encrypt to work:

- Route 53 must point `APP_DOMAIN` to the EC2 Elastic IP.
- The EC2 security group must allow inbound `80/tcp` and `443/tcp`.
- Caddy must be able to persist its `/data` Docker volume.

Create the DNS record manually:

1. Open Route 53.
2. Open the hosted zone for your domain.
3. Choose **Create record**.
4. Leave **Record name** empty when `APP_DOMAIN` is the hosted zone root, for example `example.com`.
5. Set **Record type** to `A`.
6. Set **Value** to the Elastic IP printed by `./deployment/start.sh infra`.
7. Create the record.

Alternatively, let Terraform manage the record:

```bash
MANAGE_ROUTE53_RECORD=true
ROUTE53_HOSTED_ZONE_ID=<hosted-zone-id>
```

Then rerun:

```bash
./deployment/start.sh infra
```

## 6. Deploy Docker Compose Application

Deploy the application to EC2:

```bash
./deployment/start.sh app
```

This mode:

- waits for SSH access to the EC2 instance
- ensures Docker, Docker Compose, and volume utilities are installed on the EC2 instance
- renders `app-config.json`, `Caddyfile`, Keycloak realm import, and the Compose `.env`
- mounts the two EBS volumes under `/mnt/movie-stream`
- logs in to GHCR on the EC2 instance
- pulls the published images
- runs `docker compose up -d`

The URLs are:

```text
Application: https://example.com
Gateway API: https://example.com/api
Movies API:  https://example.com/api/movies
Keycloak:    https://example.com/keycloak
pgAdmin:     https://example.com/pgadmin
```

Keycloak is publicly reachable at `/keycloak`. The admin console is password protected by `KEYCLOAK_ADMIN` and `KEYCLOAK_ADMIN_PASSWORD`.

pgAdmin is publicly reachable at `/pgadmin`. The pgAdmin login page is password protected by `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD`.

In pgAdmin, connect to the movie database with:

```text
Host: postgres-movies
Port: 5432
Database: database name from MOVIES_JDBC_URL
Username: value of MOVIES_JDBC_USERNAME
Password: value of MOVIES_JDBC_PASSWORD
```

## 7. Verify

Check DNS:

```bash
dig +short example.com
```

Check HTTPS:

```bash
curl -I https://example.com
curl -I https://example.com/keycloak/realms/movies
curl -I https://example.com/pgadmin
```

Check containers on EC2:

```bash
ssh ec2-user@<elastic-ip>
cd /opt/movie-stream
sudo docker compose --env-file .env -f docker-compose.prod.yml ps
```

## Destroy

Destroy the EC2 deployment:

```bash
./deployment/destroy.sh
```

The destroy script stops Docker Compose, unmounts the database volumes, and destroys Terraform-managed infrastructure.

The external database EBS volumes are not deleted. They are detached and remain available for a future deployment.

## Troubleshooting

### Update SSH Access After Your Public IP Changes

The EC2 security group allows SSH only from `SSH_ALLOWED_CIDR`. When your public IP changes, updating that value is
required, but it may not be sufficient by itself. If the EC2 instance was replaced or the Elastic IP was moved, SSH can
also fail because your local `~/.ssh/known_hosts` still contains the old host key for that IP address.

1. Get your current public IPv4 address:

```bash
curl -4 -s https://checkip.amazonaws.com
```

2. Update `deployment/.env.prod`:

```bash
SSH_ALLOWED_CIDR=<your-current-public-ip>/32
```

For example:

```bash
SSH_ALLOWED_CIDR=198.51.100.25/32
```

3. Remove the stale local SSH host key for the current Terraform Elastic IP before applying infrastructure changes:

```bash
ssh-keygen -R $(terraform -chdir=deployment/infra output -raw elastic_ip)
```

This refreshes your local `known_hosts` entry. It does not create a new deployment key pair in `.ssh/`.

4. Apply only the infrastructure update:

```bash
./deployment/start.sh infra
```

This updates the Terraform-managed EC2 security group rule. It does not publish Docker images or redeploy containers.

5. Verify that Terraform still points to the expected EC2 instance and Elastic IP:

```bash
terraform -chdir=deployment/infra output -raw elastic_ip
terraform -chdir=deployment/infra output -raw instance_id
```

Optionally confirm the instance details in AWS:

```bash
aws ec2 describe-instances \
  --instance-ids $(terraform -chdir=deployment/infra output -raw instance_id) \
  --query 'Reservations[0].Instances[0].{State:State.Name,PublicIp:PublicIpAddress,KeyName:KeyName,LaunchTime:LaunchTime}' \
  --output table
```

6. Retry SSH and accept the new host key:

```bash
ssh -i .ssh/movie-stream \
  -o StrictHostKeyChecking=accept-new \
  ec2-user@$(terraform -chdir=deployment/infra output -raw elastic_ip)
```

7. If Docker images are already published, redeploy only the app:

```bash
./deployment/start.sh app
```

If HTTPS is not issued, check:

- `APP_DOMAIN` resolves to the EC2 Elastic IP.
- EC2 inbound rules allow ports `80` and `443`.
- No other process on EC2 is using ports `80` or `443`.
- Caddy logs:

```bash
ssh ec2-user@<elastic-ip>
cd /opt/movie-stream
sudo docker compose --env-file .env -f docker-compose.prod.yml logs caddy
```

If Postgres does not start, check that the EBS volumes are attached and mounted:

```bash
lsblk
findmnt /mnt/movie-stream/movies-postgres
findmnt /mnt/movie-stream/keycloak-postgres
```

If the Java services cannot validate tokens, check that `KEYCLOAK_EXTERNAL_URL` is `https://example.com/keycloak` and that the generated `.env` on EC2 has:

```bash
KEYCLOAK_ISSUER_URI='https://example.com/keycloak/realms/movies'
```

## Publish And Redeploy

After changing application code, use a new `IMAGE_VERSION` and redeploy the EC2 Docker Compose application with the same
tag.

### Option A: One Command

Build, push, and redeploy with the current `IMAGE_VERSION` from `deployment/.env.prod`:

```bash
./deployment/publish-and-redeploy.sh
```

The script reads `IMAGE_VERSION` from `deployment/.env.prod`, publishes `movie-gateway`, `movies-api`, and `movies-ui` with that tag, then redeploys the EC2 containers with the same tag.

### Option B: Separate Publish And Deploy Steps

1. Set the new tag in `deployment/.env.prod`:

```bash
IMAGE_VERSION=1.2.1-users-recommended-movies
```

2. Build and publish the three application images:

```bash
./deployment/docker-publish.sh
```

This publishes:

```text
$CONTAINER_REGISTRY/movie-gateway:$IMAGE_VERSION
$CONTAINER_REGISTRY/movies-api:$IMAGE_VERSION
$CONTAINER_REGISTRY/movies-ui:$IMAGE_VERSION
```

3. Deploy the already-published images to the existing EC2 instance:

Before running the deploy command, change `IMAGE_VERSION` in `deployment/.env.prod` to the already-published tag that
EC2 should pull:

```bash
IMAGE_VERSION=2.0.0-users-recommended-movies
```

```bash
./deployment/start.sh app
```

Use this step by itself when Docker publishing already succeeded and you only need to retry deployment. Do not run
`./deployment/start.sh infra` unless you need to change infrastructure, such as updating `SSH_ALLOWED_CIDR`.

### Remote Verification

```bash
ssh -i .ssh/movie-stream ec2-user@$(terraform -chdir=deployment/infra output -raw elastic_ip)
cd /opt/movie-stream
sudo docker compose --env-file .env -f docker-compose.prod.yml ps
sudo docker compose --env-file .env -f docker-compose.prod.yml logs -f movies-api movie-gateway movies-ui
```
