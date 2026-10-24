# Adapter board fix document: make RESULTS=../results/<dir>
RESULTS ?= ../results/sweep_20261022

pcb_fix.pdf: pcb_fix.tex make_fig.py
	python3 make_fig.py $(RESULTS)
	latexmk -pdf -interaction=nonstopmode -halt-on-error -outdir=build pcb_fix.tex
	cp build/pcb_fix.pdf .

clean:
	rm -rf build pcb_fix.pdf
