#!/bin/sh

# master nodes
uvt-kvm create master01 --template ./uvtool-templates/master01.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./master_install_packages.sh
#uvt-kvm create master02 --template ./uvtool-templates/master02.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh
#uvt-kvm create master03 --template ./uvtool-templates/master03.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh

# worker nodes
uvt-kvm create worker01 --template ./uvtool-templates/worker01.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh
uvt-kvm create worker02 --template ./uvtool-templates/worker02.xml --password password --cpu 4 --memory 8192 --disk 60 --run-script-once ./install_packages.sh
