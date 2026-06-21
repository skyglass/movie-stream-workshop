output "aws_region" {
  description = "AWS region."
  value       = var.aws_region
}

output "availability_zone" {
  description = "Availability Zone used by the EC2 instance and attached EBS volumes."
  value       = var.availability_zone
}

output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.app.id
}

output "public_subnet_id" {
  description = "Public subnet ID."
  value       = aws_subnet.public.id
}

output "security_group_id" {
  description = "EC2 security group ID."
  value       = aws_security_group.app.id
}

output "instance_id" {
  description = "Movie Stream EC2 instance ID."
  value       = aws_instance.app.id
}

output "instance_public_ip" {
  description = "Current EC2 public IP."
  value       = aws_eip.app.public_ip
}

output "elastic_ip" {
  description = "Elastic IP attached to the EC2 instance."
  value       = aws_eip.app.public_ip
}

output "ssh_user" {
  description = "Default SSH user for the Amazon Linux instance."
  value       = "ec2-user"
}

output "ssh_command" {
  description = "SSH command for the EC2 instance."
  value       = "ssh ec2-user@${aws_eip.app.public_ip}"
}

output "app_url" {
  description = "Application URL."
  value       = var.app_domain != "" ? "https://${var.app_domain}" : ""
}
