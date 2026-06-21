variable "aws_region" {
  type        = string
  description = "AWS region where the EC2 deployment will be created."
}

variable "name_prefix" {
  type        = string
  description = "Prefix used for AWS resource names."
  default     = "movie-stream"
}

variable "availability_zone" {
  type        = string
  description = "Availability Zone for the public subnet, EC2 instance, and attached EBS database volumes."
  default     = "eu-central-1a"
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the deployment VPC."
  default     = "10.52.0.0/16"
}

variable "public_subnet_cidr" {
  type        = string
  description = "CIDR block for the public subnet that hosts the EC2 instance."
  default     = "10.52.0.0/24"
}

variable "ec2_instance_type" {
  type        = string
  description = "EC2 instance type used to run Docker Compose."
  default     = "t3.large"
}

variable "ec2_ami_architecture" {
  type        = string
  description = "Amazon Linux 2023 AMI architecture. Use x86_64 for t3/t3a/m instances and arm64 for t4g."
  default     = "x86_64"

  validation {
    condition     = contains(["x86_64", "arm64"], var.ec2_ami_architecture)
    error_message = "ec2_ami_architecture must be x86_64 or arm64."
  }
}

variable "ec2_ami_id" {
  type        = string
  description = "Optional explicit AMI ID. Leave empty to use the latest Amazon Linux 2023 AMI for ec2_ami_architecture."
  default     = ""
}

variable "ec2_root_volume_size" {
  type        = number
  description = "Root EBS volume size in GiB."
  default     = 30
}

variable "ssh_allowed_cidr" {
  type        = string
  description = "CIDR block allowed to SSH to the EC2 instance."
}

variable "ssh_key_name" {
  type        = string
  description = "Existing EC2 key pair name. Leave empty to create one from ssh_public_key_path."
  default     = ""
}

variable "ssh_public_key_path" {
  type        = string
  description = "Path to the SSH public key used when ssh_key_name is empty."
  default     = "../../.ssh/movie-stream.pub"
}

variable "app_domain" {
  type        = string
  description = "Public application domain, for example example.com."
  default     = ""
}

variable "manage_route53_record" {
  type        = bool
  description = "Create or update the Route 53 A record for app_domain."
  default     = false
}

variable "route53_hosted_zone_id" {
  type        = string
  description = "Route 53 hosted zone ID used when manage_route53_record is true."
  default     = ""
}

variable "movies_postgres_volume_id" {
  type        = string
  description = "Existing EBS volume ID for the movie catalog Postgres data."
  default     = ""
}

variable "keycloak_postgres_volume_id" {
  type        = string
  description = "Existing EBS volume ID for the Keycloak Postgres data."
  default     = ""
}

variable "movies_postgres_device_name" {
  type        = string
  description = "Requested EC2 device name for the movie catalog Postgres EBS volume."
  default     = "/dev/sdf"
}

variable "keycloak_postgres_device_name" {
  type        = string
  description = "Requested EC2 device name for the Keycloak Postgres EBS volume."
  default     = "/dev/sdg"
}

variable "tags" {
  type        = map(string)
  description = "Additional tags applied to AWS resources."
  default     = {}
}
