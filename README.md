# AWS EKS GitOps with ArgoCD

A GitOps-driven deployment pipeline for a Spring Boot API on AWS EKS, using ArgoCD to continuously reconcile cluster state with a Git repository instead of relying on manual `kubectl apply` or push-based CI/CD deploys.

**Live demo:** `http://a5bf4d9bd52b640119117859c1d494c1-1894595658.ap-south-1.elb.amazonaws.com/hello`
> Note: this points at infrastructure that is spun up on demand — if the link isn't responding, the cluster has likely been torn down to avoid ongoing AWS costs. See [Running it yourself](#running-it-yourself) below.

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
   ├── Managed Node Group
   └── Spring Boot API pods
          │
          pulls image from
          ▼
       AWS ECR (private registry)
          │
          exposed via
          ▼
     AWS Load Balancer (public endpoint)
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

- **VPC**: public subnets only across 2 Availability Zones, no NAT Gateway. Deliberate cost trade-off for a demo/learning cluster — a production setup would isolate workloads into private subnets (see my [`aws-vpc-architecture`](https://github.com/Sumeet-Y1/aws-vpc-architecture) project for that pattern).
- **Node group**: sized to comfortably run ArgoCD's full component set (7 pods) alongside the application — smaller instance types (e.g. `t3.micro`) hit AWS's per-node pod limits before resource limits, which is a real constraint worth knowing about when sizing EKS node groups.
- **Terraform state**: kept out of version control (`.gitignore`) since it can contain sensitive resource metadata.
- **Image pulls**: the EKS node IAM role has `AmazonEC2ContainerRegistryReadOnly` attached, so nodes authenticate to the private ECR repo automatically — no manual Kubernetes image-pull secret required.

## GitOps workflow

1. Application code changes → new Docker image built and pushed to ECR with a new tag
2. `manifests/deployment.yaml` updated to reference the new image tag
3. Change is committed and pushed to this repo
4. ArgoCD detects the diff between Git and the live cluster state
5. ArgoCD auto-syncs and reconciles the cluster to match

### Self-healing in action

With auto-sync and self-heal enabled, manually drifting the cluster from Git gets corrected automatically — no human intervention:

```
kubectl scale deployment gitops-demo-api -n default --replicas=3

NAME                               READY   STATUS        RESTARTS   AGE
gitops-demo-api-5cd44b5f99-5fjqc   1/1     Terminating   0          4s
gitops-demo-api-5cd44b5f99-9xvrq   1/1     Running       0          38m
gitops-demo-api-5cd44b5f99-zkx4l   0/1     Terminating   0          4s
```

The manual scale-to-3 was reverted within ~5 seconds — the original pod (age 38m) was untouched while the two drift-induced pods were terminated, bringing the deployment back in line with Git's `replicas: 1`.

## Running it yourself

```bash
cd terraform
terraform init
terraform apply

aws eks update-kubeconfig --region ap-south-1 --name gitops-demo-cluster

kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Then create an ArgoCD Application pointing at this repo's `manifests/` path, sync it, and the Service's LoadBalancer will provision a public endpoint automatically.

**Remember to `terraform destroy` when done** — the EKS control plane and node group bill continuously while running.

## Status

- [x] Spring Boot API with health/demo endpoint
- [x] Dockerfile (multi-stage build)
- [x] Image built and pushed to ECR
- [x] Terraform for EKS cluster, VPC, IAM roles
- [x] Kubernetes manifests (Deployment, Service)
- [x] ArgoCD installed and connected to this repo on live EKS cluster
- [x] Private ECR image pulling via node IAM role (no manual secret needed)
- [x] End-to-end sync verified with public LoadBalancer URL
- [x] Self-heal drift demo captured on real AWS infrastructure

## Related projects

This project follows on from [`aws-eks-pipeline`](https://github.com/Sumeet-Y1/aws-eks-pipeline), which handles push-based CI/CD to EKS via Jenkins. This repo replaces that push model with a pull-based GitOps approach using ArgoCD.