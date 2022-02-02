ubuntu@master01:~$ sudo kubeadm init --pod-network-cidr=192.168.50.0/24 --service-cidr=192.168.60.0/24 --control-plane-endpoint=k8s-cluster.example.com --cri-socket=/var/run/crio/crio.sock

kubectl apply -f https://docs.projectcalico.org/manifests/calico.yaml

- metal lb

https://metallb.universe.tf/installation/



