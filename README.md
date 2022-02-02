# Kubernetes : advertise load balancer IP via BGP and load balance packets by ECMP



[TOC]

### version

---



K8s:  1.21.1

CNI : calico (IP in IP)

Load balancer : metallb (BGP mode)

CSI : rook ceph

Vyos : VyOS 1.4-rolling-202105210417



```
ubuntu@master01:~$ kubectl cluster-info
                "nodeInfo": {
                    "machineID": "65fb2465b91d4ea684119c63e67fcf2e",
                    "systemUUID": "65fb2465-b91d-4ea6-8411-9c63e67fcf2e",
                    "bootID": "07a6b158-4d55-4ab4-a8b4-acddaf96acf4",
                    "kernelVersion": "5.4.0-73-generic",
                    "osImage": "Ubuntu 20.04.2 LTS",
                    "containerRuntimeVersion": "cri-o://1.21.0",
                    "kubeletVersion": "v1.21.1",
                    "kubeProxyVersion": "v1.21.1",
                    "operatingSystem": "linux",
                    "architecture": "amd64"
                },
```



```
ubuntu@master01:~$ calicoctl version
Client Version:    v3.18.4
Git commit:        dabbf416
Cluster Version:   v3.19.1
Cluster Type:      k8s,kdd,bgp,kubeadm
ubuntu@master01:~$
```



### network topology

---



All instances are running under KVM host(Ubuntu 20.04)

```

         client (192.168.123.x)
            |
            |
        linux bridge
            |
            |
            |eth0 192.168.123.20/24
          vyos
            |eth1 192.168.30.254
            |
            |
            |  192.168.30.0/24
 --------linux br----------
 |                        |
 master                worker nodes
 192.168.30.50          192.168.30.60,192.168.30.61
  (eth0)                       (eth0)
```



### set up k8s with kubeadm

---



Deploy K8s cluster under KVM host(ubuntu20.04) with kubeadm.

Master node * 1 , worker node * 2



Launch three VMs ( ubuntu 20.04) by uvtool.

```
kvm host $ cat launch-instances.sh
#!/bin/sh

# master nodes
uvt-kvm create master01 --template ./uvtool-templates/master01.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./master_install_packages.sh
#uvt-kvm create master02 --template ./uvtool-templates/master02.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh
#uvt-kvm create master03 --template ./uvtool-templates/master03.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh

# worker nodes
uvt-kvm create worker01 --template ./uvtool-templates/worker01.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh
uvt-kvm create worker02 --template ./uvtool-templates/worker02.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh


kvm host $ sh launch-instances.sh
```



Set up k8s.

```
ubuntu@master01:~$ sudo kubeadm init --pod-network-cidr=192.168.50.0/24 --service-cidr=192.168.60.0/24 --control-plane-endpoint=k8s-cluster.example.com --cri-socket=/var/run/crio/crio.sock
```



Then add worker nodes.



Install calico

```
ubuntu@master01:~$ kubectl apply -f https://docs.projectcalico.org/manifests/calico.yaml
```



Install metallb (BGP mode)

```
ubuntu@master01:~$ kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.9.6/manifests/namespace.yaml
ubuntu@master01:~$ kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.9.6/manifests/metallb.yaml
ubuntu@master01:~$ kubectl create secret generic -n metallb-system memberlist --from-literal=secretkey="$(openssl rand -base64 128)"

ubuntu@master01:~$ cat metallb-cm.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  namespace: metallb-system
  name: config
data:
  config: |
    peers:
    - peer-address: 192.168.30.254  # vyos IP
      peer-asn: 64501
      my-asn: 64500
    address-pools:
    - name: default
      protocol: bgp
      avoid-buggy-ips: true
      addresses:
      - 192.168.100.0/24
      
ubuntu@master01:~$ kubectl apply -f metallb-cm.yaml
```



```
ubuntu@master01:~$ kubectl get po -A
NAMESPACE        NAME                                            READY   STATUS      RESTARTS   AGE
default          nginx-deployment-66b6c48dd5-29lhd               1/1     Running     0          26m
default          nginx-deployment-66b6c48dd5-khzmh               1/1     Running     0          26m
kube-system      calico-kube-controllers-78d6f96c7b-dt6lw        1/1     Running     0          14h
kube-system      calico-node-5z44m                               1/1     Running     0          14h
kube-system      calico-node-6f5qc                               1/1     Running     0          14h
kube-system      calico-node-rgsvt                               1/1     Running     0          14h
kube-system      coredns-558bd4d5db-kz2wg                        1/1     Running     0          14h
kube-system      coredns-558bd4d5db-qrgrx                        1/1     Running     0          14h
kube-system      etcd-master01                                   1/1     Running     0          14h
kube-system      kube-apiserver-master01                         1/1     Running     0          14h
kube-system      kube-controller-manager-master01                1/1     Running     1          14h
kube-system      kube-proxy-bhlgx                                1/1     Running     0          14h
kube-system      kube-proxy-fxhjw                                1/1     Running     0          14h
kube-system      kube-proxy-xjdbw                                1/1     Running     0          14h
kube-system      kube-scheduler-master01                         1/1     Running     0          14h
metallb-system   controller-64f86798cc-q4p75                     1/1     Running     0          14h
metallb-system   speaker-d7rmm                                   1/1     Running     0          14h
metallb-system   speaker-jbsb2                                   1/1     Running     0          14h
metallb-system   speaker-q72w5                                   1/1     Running     0          14h
rook-ceph        csi-cephfsplugin-99gdv                          3/3     Running     0          12h
rook-ceph        csi-cephfsplugin-mjfrp                          3/3     Running     0          12h
rook-ceph        csi-cephfsplugin-provisioner-6f75644874-mrw7n   6/6     Running     0          12h
rook-ceph        csi-cephfsplugin-provisioner-6f75644874-nq2tq   6/6     Running     0          12h
rook-ceph        csi-rbdplugin-6t2q7                             3/3     Running     0          12h
rook-ceph        csi-rbdplugin-provisioner-67fb987799-2fmnd      6/6     Running     0          12h
rook-ceph        csi-rbdplugin-provisioner-67fb987799-9fknv      6/6     Running     0          12h
rook-ceph        csi-rbdplugin-qpqgt                             3/3     Running     0          12h
rook-ceph        rook-ceph-mgr-a-766f984fb5-x8crv                1/1     Running     0          12h
rook-ceph        rook-ceph-mon-a-bf9964ffc-wrf7n                 1/1     Running     0          12h
rook-ceph        rook-ceph-operator-7c77d8fbb9-prd72             1/1     Running     0          13h
rook-ceph        rook-ceph-osd-0-756df4d54b-gcdjb                1/1     Running     0          12h
rook-ceph        rook-ceph-osd-1-944684b86-5n52z                 1/1     Running     0          12h
rook-ceph        rook-ceph-osd-prepare-worker01-26tv9            0/1     Completed   0          12h
rook-ceph        rook-ceph-osd-prepare-worker02-v7z48            0/1     Completed   0          12h
rook-ceph        rook-ceph-tools-fc5f9586c-jbxtf                 1/1     Running     0          9h
rook-ceph        rook-discover-dr9tl                             1/1     Running     0          13h
rook-ceph        rook-discover-qvmdx                             1/1     Running     0          13h
ubuntu@master01:~$
```



### vyos config

---



See `vyos_config/config.boot`



```
vyos@vyos:~$ uname -ri
5.10.38-amd64-vyos unknown

vyos@vyos:~$ show version

Version:          VyOS 1.4-rolling-202105210417
Release Train:    sagitta

Built by:         autobuild@vyos.net
Built on:         Sat 22 May 2021 01:17 UTC
Build UUID:       5246be6e-6f0e-4ab5-8586-26311de3ab8f
Build Commit ID:  4e0a56eaa2aa51

Architecture:     x86_64
Boot via:         installed image
System type:      KVM guest

Hardware vendor:  QEMU
Hardware model:   Standard PC (Q35 + ICH9, 2009)
Hardware S/N:
Hardware UUID:    5e8d2880-7d2d-4e2a-ab6b-b0a36f561246

Copyright:        VyOS maintainers and contributors
vyos@vyos:~$
```



```
vyos@vyos:~$ show configuration commands
set firewall all-ping 'enable'
set firewall broadcast-ping 'disable'
set firewall config-trap 'disable'
set firewall ipv6-receive-redirects 'disable'
set firewall ipv6-src-route 'disable'
set firewall ip-src-route 'disable'
set firewall log-martians 'enable'
set firewall receive-redirects 'disable'
set firewall send-redirects 'disable'
set firewall source-validation 'disable'
set firewall syn-cookies 'enable'
set firewall twa-hazards-protection 'disable'
set interfaces ethernet eth0 address 'dhcp'
set interfaces ethernet eth0 hw-id '52:54:00:66:72:df'
set interfaces ethernet eth1 address '192.168.30.254/24'
set interfaces ethernet eth1 hw-id '52:54:00:12:81:fe'
set interfaces ethernet eth2 hw-id '52:54:00:44:cd:06'
set interfaces loopback lo
set nat source rule 1 outbound-interface 'eth0'
set nat source rule 1 source address '192.168.30.0/24'
set nat source rule 1 translation address 'masquerade'
set protocols bgp address-family ipv4-unicast maximum-paths ebgp '8'
set protocols bgp address-family ipv4-unicast maximum-paths ibgp '8'
set protocols bgp address-family ipv4-unicast network 192.168.100.0/24
set protocols bgp local-as '64501'
set protocols bgp neighbor 192.168.30.50 peer-group 'group01'
set protocols bgp neighbor 192.168.30.60 peer-group 'group01'
set protocols bgp neighbor 192.168.30.61 peer-group 'group01'
set protocols bgp peer-group group01 address-family ipv4-unicast soft-reconfiguration inbound
set protocols bgp peer-group group01 remote-as '64500'
set protocols bgp peer-group group01 update-source '192.168.30.254'
set service dhcp-server listen-address '192.168.30.254'
set service dhcp-server shared-network-name net01 authoritative
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 default-router '192.168.30.254'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 dns-server '1.1.1.1'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 dns-server '8.8.8.8'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 domain-name 'example.com'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 lease '10800'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 range 0 start '192.168.30.10'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 range 0 stop '192.168.30.30'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping master01 ip-address '192.168.30.50'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping master01 mac-address '52:54:00:88:9c:01'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping master02 ip-address '192.168.30.51'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping master02 mac-address '52:54:00:88:9c:02'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping master03 ip-address '192.168.30.52'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping master03 mac-address '52:54:00:88:9c:03'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping worker01 ip-address '192.168.30.60'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping worker01 mac-address '52:54:00:88:9c:a1'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping worker02 ip-address '192.168.30.61'
set service dhcp-server shared-network-name net01 subnet 192.168.30.0/24 static-mapping worker02 mac-address '52:54:00:88:9c:a2'
set service ssh
set system config-management commit-revisions '10'
set system console device ttyS0 speed '115200'
set system host-name 'vyos'
set system login user vyos authentication encrypted-password '$6$BbUzJ9k6Lum$U3EcpwqgrvF1g8xfJKA9LFkrMg2VrDJzzmmZfN8MmThyatMjo74qVUH6BF0htoISn8V4I5Y6dFa9NLxOOT6oF.'
set system login user vyos authentication plaintext-password ''
set system ntp server 0.pool.ntp.org
set system ntp server 1.pool.ntp.org
set system ntp server 2.pool.ntp.org
set system syslog global facility all level 'info'
set system syslog global facility protocols level 'debug'
vyos@vyos:~$
```



### BGP testing

---



Vyos has not received routing information via the BGP peer yet.

```
vyos@vyos:~$ show ip route bgp
vyos@vyos:~$
```



Create pods and a service.

```
ubuntu@master01:~$ cat deployment-nginx.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.14.2
        ports:
        - containerPort: 80
ubuntu@master01:~$

ubuntu@master01:~$ cat service-nginx.yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  loadBalancerIP: 192.168.100.10
  type: LoadBalancer
  selector:
    app: nginx
  ports:
    - name: http
      protocol: TCP
      port: 80
      targetPort: 80
ubuntu@master01:~$

ubuntu@master01:~$ kubectl apply -f deployment-nginx.yaml
ubuntu@master01:~$ kubectl apply -f service-nginx.yaml

ubuntu@master01:~$ kubectl get po -o wide
NAME                                READY   STATUS    RESTARTS   AGE   IP               NODE       NOMINATED NODE   READINESS GATES
nginx-deployment-66b6c48dd5-29lhd   1/1     Running   0          25m   192.168.50.206   worker01   <none>           <none>
nginx-deployment-66b6c48dd5-khzmh   1/1     Running   0          25m   192.168.50.205   worker01   <none>           <none>

ubuntu@master01:~$ kubectl get svc
NAME         TYPE           CLUSTER-IP       EXTERNAL-IP      PORT(S)        AGE
kubernetes   ClusterIP      192.168.60.1     <none>           443/TCP        14h
my-service   LoadBalancer   192.168.60.147   192.168.100.10   80:30686/TCP   25m

ubuntu@master01:~$ kubectl get ep
NAME         ENDPOINTS                             AGE
kubernetes   192.168.30.50:6443                    14h
my-service   192.168.50.205:80,192.168.50.206:80   25m
ubuntu@master01:~$
```



Vyos router received the routing info via BGP.

Vyos will route packets destined to 192.168.100.10(load balancer IP) to 192.168.30.50 or 192.168.30.60 or 192.168.30.61 by ECMP.

```
vyos@vyos:~$ show ip route bgp
Codes: K - kernel route, C - connected, S - static, R - RIP,
       O - OSPF, I - IS-IS, B - BGP, E - EIGRP, N - NHRP,
       T - Table, v - VNC, V - VNC-Direct, A - Babel, D - SHARP,
       F - PBR, f - OpenFabric,
       > - selected route, * - FIB route, q - queued, r - rejected, b - backup

B>* 192.168.100.10/32 [20/0] via 192.168.30.50, eth1, weight 1, 00:00:54  # master01
  *                          via 192.168.30.60, eth1, weight 1, 00:00:54  # worker01
  *                          via 192.168.30.61, eth1, weight 1, 00:00:54  # worker02
vyos@vyos:~$
```



Confirm ECMP is working.



Launch three clients on kvm host.

```
kvm host $ lxc profile show virbr0-profile
config: {}
description: Default LXD profile
devices:
  eth0:
    name: eth0
    nictype: bridged
    parent: virbr0
    type: nic
  root:
    path: /
    pool: mypool
    type: disk
name: virbr0-profile  # this bridge belongs to 192.168.123.0/24
used_by: []
kvm host $

kvm host $ for i in `seq 1 3`;do lxc launch 20.04-c -p virbr0-profile c0$i ; done

kvm host $ lxc list -c n4
+------+-----------------------+
| NAME |         IPV4          |
+------+-----------------------+
| c01  | 192.168.123.5 (eth0)  |
+------+-----------------------+
| c02  | 192.168.123.23 (eth0) |
+------+-----------------------+
| c03  | 192.168.123.29 (eth0) |
+------+-----------------------+

# add static route. 192.168.123.20 is vyos's IP.
kvm host $ lxc exec c01 -- ip route add 192.168.100.0/24 via 192.168.123.20
kvm host $ lxc exec c02 -- ip route add 192.168.100.0/24 via 192.168.123.20
kvm host $ lxc exec c03 -- ip route add 192.168.100.0/24 via 192.168.123.20

# confirm the client can access to the load balancer ip.

kvm host $ lxc exec c01 -- ip r g 192.168.100.10
192.168.100.10 via 192.168.123.20 dev eth0 src 192.168.123.5 uid 0
    cache
    
kvm host $ lxc exec c01 -- curl 192.168.100.10
<!DOCTYPE html>
<html>
<head>
<title>Welcome to nginx!</title>
<style>
    body {
        width: 35em;
        margin: 0 auto;
        font-family: Tahoma, Verdana, Arial, sans-serif;
    }
</style>
</head>
<body>
<h1>Welcome to nginx!</h1>
<p>If you see this page, the nginx web server is successfully installed and
working. Further configuration is required.</p>

<p>For online documentation and support please refer to
<a href="http://nginx.org/">nginx.org</a>.<br/>
Commercial support is available at
<a href="http://nginx.com/">nginx.com</a>.</p>

<p><em>Thank you for using nginx.</em></p>
</body>
</html>
```



capture packets to see destination MAC address.

```
vyos@vyos:~$ ip r g 192.168.100.10
192.168.100.10 via 192.168.30.61 dev eth1 src 192.168.30.254 uid 1002
    cache
    
vyos@vyos:~$ sudo tcpdump -nnei eth1 dst host 192.168.100.10 | grep GET
```



On kvm host.

```
$ for i in `seq 1 100`; do for i in `seq 1 3`;do lxc exec c0$i -- curl 192.168.100.10 1>/dev/null 2>/dev/null; sleep 3;done ; done
```



Vyos routed packets destined to 192.168.100.10(load balancer IP) to 52:54:00:88:9c:01 or 52:54:00:88:9c:a1 or 52:54:00:88:9c:a2.

So ECMP is working.

```
vyos@vyos:~$ sudo tcpdump -nnei eth1 dst host 192.168.100.10 | grep GET
tcpdump: verbose output suppressed, use -v or -vv for full protocol decode
listening on eth1, link-type EN10MB (Ethernet), capture size 262144 bytes
16:59:02.216579 52:54:00:12:81:fe > 52:54:00:88:9c:a1, ethertype IPv4 (0x0800), length 144: 192.168.123.5.35896 > 192.168.100.10.80: Flags [P.], seq 0:78, ack 1, win 502, options [nop,nop,TS val 1911206777 ecr 2354659571], length 78: HTTP: GET / HTTP/1.1
16:59:05.382223 52:54:00:12:81:fe > 52:54:00:88:9c:01, ethertype IPv4 (0x0800), length 144: 192.168.123.23.59208 > 192.168.100.10.80: Flags [P.], seq 0:78, ack 1, win 502, options [nop,nop,TS val 2477999263 ecr 2955523281], length 78: HTTP: GET / HTTP/1.1
16:59:08.510116 52:54:00:12:81:fe > 52:54:00:88:9c:a2, ethertype IPv4 (0x0800), length 144: 192.168.123.29.52330 > 192.168.100.10.80: Flags [P.], seq 0:78, ack 1, win 502, options [nop,nop,TS val 3997457386 ecr 1355801885], length 78: HTTP: GET / HTTP/1.1
16:59:11.626671 52:54:00:12:81:fe > 52:54:00:88:9c:a1, ethertype IPv4 (0x0800), length 144: 192.168.123.5.35902 > 192.168.100.10.80: Flags [P.], seq 0:78, ack 1, win 502, options [nop,nop,TS val 1911216187 ecr 2354668981], length 78: HTTP: GET / HTTP/1.1
16:59:14.802759 52:54:00:12:81:fe > 52:54:00:88:9c:01, ethertype IPv4 (0x0800), length 144: 192.168.123.23.59214 > 192.168.100.10.80: Flags [P.], seq 0:78, ack 1, win 502, options [nop,nop,TS val 2478008683 ecr 2955532701], length 78: HTTP: GET / HTTP/1.1
```



```
vyos@vyos:~$ show arp
Address                  HWtype  HWaddress           Flags Mask            Iface
192.168.123.8            ether   00:16:3e:89:66:e7   C                     eth0
192.168.123.1            ether   52:54:00:f1:9a:1f   C                     eth0
192.168.123.23           ether   00:16:3e:b6:31:4f   C                     eth0
192.168.123.5            ether   00:16:3e:8b:71:34   C                     eth0
192.168.30.61            ether   52:54:00:88:9c:a2   C                     eth1  # worker02
192.168.123.29           ether   00:16:3e:8a:2d:95   C                     eth0
192.168.30.60            ether   52:54:00:88:9c:a1   C                     eth1  # worker01
192.168.30.52            ether   52:54:00:88:9c:03   C                     eth1
192.168.30.51            ether   52:54:00:88:9c:02   C                     eth1
192.168.123.41           ether   00:16:3e:17:ef:3c   C                     eth0
192.168.30.50            ether   52:54:00:88:9c:01   C                     eth1  # master01
vyos@vyos:~$
```



Delete the service.

```
ubuntu@master01:~$ kubectl get svc
NAME         TYPE           CLUSTER-IP       EXTERNAL-IP      PORT(S)        AGE
kubernetes   ClusterIP      192.168.60.1     <none>           443/TCP        14h
my-service   LoadBalancer   192.168.60.147   192.168.100.10   80:30686/TCP   31m
ubuntu@master01:~$ kubectl delete svc my-service
service "my-service" deleted
ubuntu@master01:~$
```


The routing info has gone.
```
vyos@vyos:~$ show ip route bgp
vyos@vyos:~$
```

