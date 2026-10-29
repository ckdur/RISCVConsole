# Build the RO report from a results directory made by ../sim/ro_sim.py
#   make                                   newest ../results/*/ (by name)
#   make RESULTS=../results/sim_YYYYMMDD
# The PDF lands in $(RESULTS)/report/report.pdf
RESULTS ?= $(patsubst %/sim.csv,%,$(lastword $(sort $(wildcard ../results/*/sim.csv))))

.PHONY: report clean
report:
	@test -n "$(RESULTS)" || (echo "No results directory, run ../sim/ro_sim.py first" && false)
	python3 make_report.py $(RESULTS)

clean:
	rm -rf $(RESULTS)/report
