output "aws_region" {
  description = "AWS region."
  value       = var.aws_region
}

output "cluster_name" {
  description = "EKS cluster name."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API endpoint."
  value       = module.eks.cluster_endpoint
}

output "vpc_id" {
  description = "VPC ID."
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "Private subnet IDs used by EKS nodes."
  value       = module.vpc.private_subnets
}

output "public_subnet_ids" {
  description = "Public subnet IDs used by public load balancers."
  value       = module.vpc.public_subnets
}

output "node_security_group_id" {
  description = "EKS managed node security group ID."
  value       = module.eks.node_security_group_id
}

output "cluster_security_group_id" {
  description = "EKS cluster security group ID."
  value       = module.eks.cluster_security_group_id
}

output "nat_public_ips" {
  description = "NAT gateway public IPs."
  value       = module.vpc.nat_public_ips
}

output "stateful_availability_zone" {
  description = "Availability zone for the dedicated stateful node group."
  value       = var.stateful_availability_zone
}

output "stateful_private_subnet_id" {
  description = "Private subnet ID used by the dedicated stateful node group."
  value       = local.stateful_subnet_id
}
