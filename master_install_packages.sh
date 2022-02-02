#!/bin/sh
sudo apt update
sudo apt -y install curl apt-transport-https
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt update
sudo apt -y install vim git curl wget kubelet kubeadm kubectl
sudo apt-mark hold kubelet kubeadm kubectl

sudo sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab
sudo swapoff -a

sudo modprobe overlay
sudo modprobe br_netfilter

sudo tee /etc/sysctl.d/kubernetes.conf<<EOF
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
EOF

sudo sysctl --system

sudo sh -c "echo overlay | tee -a /etc/modules"
sudo sh -c "echo br_netfilter | tee -a /etc/modules"

# Add repo
OS=xUbuntu_20.04
VERSION=1.21

sudo sh -c "echo 'deb https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/$OS/ /' > /etc/apt/sources.list.d/devel:kubic:libcontainers:stable.list"
sudo sh -c "echo 'deb https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable:/cri-o:/$VERSION/$OS/ /' > /etc/apt/sources.list.d/devel:kubic:libcontainers:stable:cri-o:$VERSION.list"

curl -L https://download.opensuse.org/repositories/devel:kubic:libcontainers:stable:cri-o:$VERSION/$OS/Release.key | sudo apt-key add -
curl -L https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/$OS/Release.key | sudo apt-key add -

sudo apt update

# Install CRI-O
sudo apt install cri-o cri-o-runc -y

# Start and enable Service
sudo systemctl daemon-reload
sudo systemctl start crio
sudo systemctl enable crio

# update /etc/hosts
sudo tee /etc/hosts<<EOF
127.0.0.1 localhost
192.168.30.50 k8s-cluster k8s-cluster.example.com
192.168.30.50 master01 master01.example.com
192.168.30.51 master02 master02.example.com
192.168.30.52 master03 master03.example.com
192.168.30.60 worker01 worker01.example.com
192.168.30.61 worker02 worker02.example.com
EOF

echo 'source <(kubectl completion bash)' >>~/.bashrc
sudo sh -c " kubectl completion bash >/etc/bash_completion.d/kubectl"

sudo kubeadm config images pull
echo "### end ###"
