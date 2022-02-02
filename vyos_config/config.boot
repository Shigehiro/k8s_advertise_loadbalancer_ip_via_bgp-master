firewall {
    all-ping enable
    broadcast-ping disable
    config-trap disable
    ipv6-receive-redirects disable
    ipv6-src-route disable
    ip-src-route disable
    log-martians enable
    receive-redirects disable
    send-redirects disable
    source-validation disable
    syn-cookies enable
    twa-hazards-protection disable
}
interfaces {
    ethernet eth0 {
        address dhcp
        hw-id 52:54:00:66:72:df
    }
    ethernet eth1 {
        address 192.168.30.254/24
        hw-id 52:54:00:12:81:fe
    }
    ethernet eth2 {
        hw-id 52:54:00:44:cd:06
    }
    loopback lo {
    }
}
nat {
    source {
        rule 1 {
            outbound-interface eth0
            source {
                address 192.168.30.0/24
            }
            translation {
                address masquerade
            }
        }
    }
}
protocols {
    bgp {
        address-family {
            ipv4-unicast {
                maximum-paths {
                    ebgp 8
                    ibgp 8
                }
                network 192.168.100.0/24 {
                }
            }
        }
        local-as 64501
        neighbor 192.168.30.50 {
            peer-group group01
        }
        neighbor 192.168.30.60 {
            peer-group group01
        }
        neighbor 192.168.30.61 {
            peer-group group01
        }
        peer-group group01 {
            address-family {
                ipv4-unicast {
                    soft-reconfiguration {
                        inbound
                    }
                }
            }
            remote-as 64500
            update-source 192.168.30.254
        }
    }
}
service {
    dhcp-server {
        listen-address 192.168.30.254
        shared-network-name net01 {
            authoritative
            subnet 192.168.30.0/24 {
                default-router 192.168.30.254
                dns-server 1.1.1.1
                dns-server 8.8.8.8
                domain-name example.com
                lease 10800
                range 0 {
                    start 192.168.30.10
                    stop 192.168.30.30
                }
                static-mapping master01 {
                    ip-address 192.168.30.50
                    mac-address 52:54:00:88:9c:01
                }
                static-mapping master02 {
                    ip-address 192.168.30.51
                    mac-address 52:54:00:88:9c:02
                }
                static-mapping master03 {
                    ip-address 192.168.30.52
                    mac-address 52:54:00:88:9c:03
                }
                static-mapping worker01 {
                    ip-address 192.168.30.60
                    mac-address 52:54:00:88:9c:a1
                }
                static-mapping worker02 {
                    ip-address 192.168.30.61
                    mac-address 52:54:00:88:9c:a2
                }
            }
        }
    }
    ssh {
    }
}
system {
    config-management {
        commit-revisions 10
    }
    console {
        device ttyS0 {
            speed 115200
        }
    }
    host-name vyos
    login {
        user vyos {
            authentication {
                encrypted-password $6$BbUzJ9k6Lum$U3EcpwqgrvF1g8xfJKA9LFkrMg2VrDJzzmmZfN8MmThyatMjo74qVUH6BF0htoISn8V4I5Y6dFa9NLxOOT6oF.
                plaintext-password ""
            }
        }
    }
    ntp {
        server 0.pool.ntp.org {
        }
        server 1.pool.ntp.org {
        }
        server 2.pool.ntp.org {
        }
    }
    syslog {
        global {
            facility all {
                level info
            }
            facility protocols {
                level debug
            }
        }
    }
}


// Warning: Do not remove the following line.
// vyos-config-version: "bgp@1:broadcast-relay@1:cluster@1:config-management@1:conntrack@2:conntrack-sync@2:dhcp-relay@2:dhcp-server@5:dhcpv6-server@1:dns-forwarding@3:firewall@5:https@2:interfaces@20:ipoe-server@1:ipsec@5:isis@1:l2tp@3:lldp@1:mdns@1:nat@5:nat66@1:ntp@1:pppoe-server@5:pptp@2:qos@1:quagga@9:rpki@1:salt@1:snmp@2:ssh@2:sstp@3:system@21:vrf@2:vrrp@2:vyos-accel-ppp@2:wanloadbalance@3:webproxy@2:zone-policy@1"
// Release version: 1.4-rolling-202105210417
