# Build the ADC report from a results directory made by ../adc_sweep.sh
#   make                                   newest ../results/*/ (by name)
#   make RESULTS=../results/sweep_YYYYMMDD
# The PDF lands in $(RESULTS)/report/report.pdf
RESULTS ?= $(patsubst %/runs.csv,%,$(lastword $(sort $(wildcard ../results/*/runs.csv))))

.PHONY: report clean
report:
	@test -n "$(RESULTS)" || (echo "No results directory, run ../adc_sweep.sh first" && false)
	python3 make_report.py $(RESULTS)

clean:
	rm -rf $(RESULTS)/report
