#!/bin/bash

declare -a arr=("1" "4" "7" "8")
policyName[1]="lru"
policyName[2]="lrfu"
policyName[3]="lru2"
policyName[4]="arc"
policyName[5]="twoqueue"
policyName[6]="opt"
policyName[7]="mq"
policyName[8]="lirs"

seed="12345678"

for edge in "${arr[@]}"; do
	for aggr in "${arr[@]}"; do
		for core in "${arr[@]}"; do
			echo "${policyName[${edge}]} ${policyName[${aggr}]} ${policyName[${core}]}"
			cd build
			java CacheSim ../src/fattree-128node-k8.txt ../../spark-traces-aws-cluster-2017-11-22/combined-interleaved-reads-no-lr-no-wordcount ${edge} ${aggr} ${core} ${1} 8 ${seed} >../../caching-sim-results/${policyName[${edge}]}-${policyName[${aggr}]}-${policyName[${core}]}-${1}-combined-interleaved-reads-${seed}.csv
			cd ..
		done
	done
done
