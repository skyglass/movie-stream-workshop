provider "aws" {
  region = var.aws_region
}

locals {
  ami_name_pattern = (
    var.ec2_ami_architecture == "arm64"
    ? "al2023-ami-2023.*-kernel-6.1-arm64"
    : "al2023-ami-2023.*-kernel-6.1-x86_64"
  )

  ssh_key_name = var.ssh_key_name != "" ? var.ssh_key_name : aws_key_pair.app[0].key_name

  tags = merge(
    {
      Project   = "movie-stream-workshop"
      ManagedBy = "terraform"
    },
    var.tags
  )
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = [local.ami_name_pattern]
  }

  filter {
    name   = "architecture"
    values = [var.ec2_ami_architecture]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_key_pair" "app" {
  count = var.ssh_key_name == "" ? 1 : 0

  key_name   = "${var.name_prefix}-deploy"
  public_key = file(pathexpand(var.ssh_public_key_path))

  tags = local.tags
}

resource "aws_vpc" "app" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-vpc"
  })
}

resource "aws_internet_gateway" "app" {
  vpc_id = aws_vpc.app.id

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-igw"
  })
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.app.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-public-${var.availability_zone}"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.app.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.app.id
  }

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-public"
  })
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "app" {
  name        = "${var.name_prefix}-ec2"
  description = "Movie Stream EC2 ingress."
  vpc_id      = aws_vpc.app.id

  ingress {
    description = "HTTP for Lets Encrypt and redirect to HTTPS"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "SSH deployment access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  egress {
    description = "Outbound internet access"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-ec2"
  })
}

resource "aws_instance" "app" {
  ami                         = var.ec2_ami_id != "" ? var.ec2_ami_id : data.aws_ami.amazon_linux.id
  instance_type               = var.ec2_instance_type
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.app.id]
  key_name                    = local.ssh_key_name
  associate_public_ip_address = true

  user_data_replace_on_change = true
  user_data                   = <<-EOF
    #!/bin/bash
    set -euxo pipefail

    dnf update -y
    dnf install -y docker util-linux xfsprogs
    if ! command -v curl >/dev/null 2>&1; then
      dnf install -y curl-minimal
    fi
    systemctl enable --now docker
    usermod -aG docker ec2-user

    mkdir -p /usr/local/lib/docker/cli-plugins
    arch="$(uname -m)"
    case "$arch" in
      x86_64) compose_arch="x86_64" ;;
      aarch64) compose_arch="aarch64" ;;
      *) echo "Unsupported architecture for Docker Compose: $arch" >&2; exit 1 ;;
    esac
    curl -fsSL \
      "https://github.com/docker/compose/releases/download/v2.40.3/docker-compose-linux-$compose_arch" \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

    mkdir -p /opt/movie-stream
    chown ec2-user:ec2-user /opt/movie-stream
  EOF

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.ec2_root_volume_size
    encrypted             = true
    delete_on_termination = true

    tags = merge(local.tags, {
      Name = "${var.name_prefix}-root"
    })
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-app"
  })
}

resource "aws_eip" "app" {
  domain = "vpc"

  tags = merge(local.tags, {
    Name = "${var.name_prefix}-app"
  })
}

resource "aws_eip_association" "app" {
  allocation_id = aws_eip.app.id
  instance_id   = aws_instance.app.id
}

resource "aws_volume_attachment" "movies_postgres" {
  count = var.movies_postgres_volume_id != "" ? 1 : 0

  device_name = var.movies_postgres_device_name
  volume_id   = var.movies_postgres_volume_id
  instance_id = aws_instance.app.id
}

resource "aws_volume_attachment" "keycloak_postgres" {
  count = var.keycloak_postgres_volume_id != "" ? 1 : 0

  device_name = var.keycloak_postgres_device_name
  volume_id   = var.keycloak_postgres_volume_id
  instance_id = aws_instance.app.id
}

resource "aws_route53_record" "app" {
  count = var.manage_route53_record && var.route53_hosted_zone_id != "" ? 1 : 0

  zone_id = var.route53_hosted_zone_id
  name    = var.app_domain
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}
