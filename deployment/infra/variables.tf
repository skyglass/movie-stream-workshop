variable "aws_region" {
  type        = string
  description = "AWS region where the EKS cluster and VPC will be created."
}

variable "name_prefix" {
  type        = string
  description = "Prefix used for AWS resource names."
  default     = "movie-stream"
}

variable "cluster_version" {
  type        = string
  description = "EKS Kubernetes version."
  default     = "1.32"
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the EKS VPC."
  default     = "10.52.0.0/16"
}

variable "private_subnet_cidrs" {
  type        = list(string)
  description = "Private subnet CIDRs for EKS worker nodes."
  default     = ["10.52.0.0/19", "10.52.32.0/19", "10.52.64.0/19"]
}

variable "public_subnet_cidrs" {
  type        = list(string)
  description = "Public subnet CIDRs for NAT gateways and public load balancers."
  default     = ["10.52.96.0/22", "10.52.100.0/22", "10.52.104.0/22"]
}

variable "single_nat_gateway" {
  type        = bool
  description = "Use a single NAT gateway to reduce workshop cost. Use false for stronger production AZ isolation."
  default     = true
}

variable "node_instance_types" {
  type        = list(string)
  description = "Instance types for the default managed node group."
  default     = ["m6i.large"]
}

variable "node_min_size" {
  type        = number
  description = "Minimum node count."
  default     = 2
}

variable "node_desired_size" {
  type        = number
  description = "Desired node count."
  default     = 2
}

variable "node_max_size" {
  type        = number
  description = "Maximum node count."
  default     = 4
}

variable "stateful_availability_zone" {
  type        = string
  description = "Availability zone for the dedicated node group that can mount pre-created EBS volumes used by Postgres."
  default     = "eu-central-1a"
}

variable "stateful_node_instance_types" {
  type        = list(string)
  description = "Instance types for the stateful node group pinned to stateful_availability_zone."
  default     = ["m6i.large"]
}

variable "stateful_node_min_size" {
  type        = number
  description = "Minimum node count for the stateful node group."
  default     = 1
}

variable "stateful_node_desired_size" {
  type        = number
  description = "Desired node count for the stateful node group."
  default     = 1
}

variable "stateful_node_max_size" {
  type        = number
  description = "Maximum node count for the stateful node group."
  default     = 2
}

variable "tags" {
  type        = map(string)
  description = "Additional tags applied to AWS resources."
  default     = {}
}
