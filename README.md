# AWS EKS GitOps with ArgoCD

A GitOps-driven deployment pipeline for a Spring Boot API on AWS EKS, using ArgoCD to continuously reconcile cluster state with a Git repository instead of relying on manual `kubectl apply` or push-based CI/CD deploys.

## Architecture

```
GitHub (this repo)
   │
   │  manifests/ (Deployment, Service)
   ▼
ArgoCD (running in-cluster)
   │  watches repo, detects drift, auto-syncs
   ▼
AWS EKS Cluster
   │
   ├── VPC (2 AZs, public subnets)
   ├── Managed Node Group (t3.micro, Free Tier eligible)
   └── Spring Boot API pods
          │
          pulls image from
          ▼
       AWS ECR (private registry)
```

**Core idea:** instead of CI/CD pushing changes directly into the cluster, ArgoCD runs *inside* the cluster and pulls desired state from Git. If the live cluster ever drifts from what's declared in Git — whether from a manual `kubectl` change or a failed deploy — ArgoCD detects it and reconciles automatically. Git is the single source of truth.

## Stack

| Layer | Tool |
|---|---|
| Application | Spring Boot 4.0 (Java 21) |
| Containerization | Docker (multi-stage build) |
| Image Registry | AWS ECR |
| Infrastructure as Code | Terraform |
| Compute | AWS EKS (managed node group) |
| GitOps / CD | ArgoCD |
| Cloud | AWS (`ap-south-1`) |

## Repo structure

```
.
├── src/                    # Spring Boot application source
├── Dockerfile               # Multi-stage build (JDK build → slim JRE runtime)
├── terraform/               # EKS cluster, VPC, IAM roles as code
│   ├── provider.tf
│   ├── variables.tf
│   ├── vpc.tf
│   ├── eks.tf
│   └── outputs.tf
└── manifests/                # Kubernetes manifests — the source of truth ArgoCD syncs from
    ├── deployment.yaml
    └── service.yaml
```

## Infrastructure design notes

- **VPC**: public subnets only across 2 Availability Zones, no NAT Gateway. This is a deliberate cost trade-off for a demo/learning cluster — a production setup would isolate workloads into private subnets (see my [`aws-vpc-architecture`](https://github.com/Sumeet-Y1/aws-vpc-architecture) project for that pattern).
- **Node group**: `t3.micro`, sized to stay within AWS Free Tier during development and testing.
- **Terraform state**: kept out of version control (`.gitignore`) since it can contain sensitive resource metadata.

## GitOps workflow

1. Application code changes → new Docker image built and pushed to ECR with a new tag
2. `manifests/deployment.yaml` updated to reference the new image tag
3. Change is committed and pushed to this repo
4. ArgoCD detects the diff between Git and the live cluster state
5. ArgoCD syncs automatically (or on manual trigger, depending on sync policy) and reconciles the cluster to match
6. If anyone manually modifies the cluster directly (e.g. `kubectl scale`), ArgoCD's self-heal detects the drift and reverts it back to match Git — without human intervention

## Status

- [x] Spring Boot API with health/demo endpoint
- [x] Dockerfile (multi-stage build)
- [x] Image built and pushed to ECR
- [x] Terraform for EKS cluster, VPC, IAM roles
- [x] Kubernetes manifests (Deployment, Service)
- [ ] ArgoCD installed and connected to this repo on live EKS cluster
- [ ] ECR image pull secret configured for cluster
- [ ] End-to-end sync verified with public LoadBalancer URL
- [ ] Self-heal drift demo captured (logs/recording)

## Related projects

This project follows on from [`aws-eks-pipeline`](https://github.com/Sumeet-Y1/aws-eks-pipeline), which handles push-based CI/CD to EKS via Jenkins. This repo replaces that push model with a pull-based GitOps approach using ArgoCD.