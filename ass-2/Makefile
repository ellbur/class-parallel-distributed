
GTK_CFLAGS  = $(shell pkg-config --cflags gtk+-2.0)
GTK_LDFLAGS = $(shell pkg-config --libs gtk+-2.0)

MPICC = mpicc

all-mpi: mandelbrot-nodisp mpi-static mpi-dyn mpi-static-shuffle \
	mpi-dyn-shuffle

all-omp: mandelbrot-nodisp omp-static omp-dyn omp-static-shuffle omp-dyn-shuffle

mandelbrot: mandelbrot.c
	gcc $< -o $@ -Wall --std=c99 $(GTK_CFLAGS) $(GTK_LDFLAGS) -lcrypt -O3

mandelbrot-nodisp: mandelbrot_nodisp.c
	gcc $< -o $@ -Wall --std=c99 -lm -lcrypt -O3

mpi-static: mpi_static.c fractal.c measure.c
	$(MPICC) $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm -mpe=mpilog -O3

mpi-dyn: mpi_dyn.c fractal.c measure.c
	mpicc $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm -mpe=mpilog -O3

mpi-static-shuffle: mpi_static_shuffle.c fractal.c measure.c
	mpicc $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm -mpe=mpilog -O3
	
mpi-dyn-shuffle: mpi_dyn_shuffle.c fractal.c measure.c
	mpicc $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm -mpe=mpilog -O3

omp-static: omp.c fractal.c
	gcc $< fractal.c -o $@ -Wall -Werror --std=c99 -lcrypt -lm -fopenmp -O3 -Dreally_parallel
    
omp-dyn: omp.c fractal.c
	gcc $< fractal.c -o $@ -Wall -Werror --std=c99 -lcrypt -lm -fopenmp -O3 -Dreally_parallel -Duse_dynamic
    
omp-static-fake: omp.c fractal.c
	gcc $< fractal.c -o $@ -Wall -Werror --std=c99 -lcrypt -lm -fopenmp -O3
    
omp-static-shuffle: omp.c fractal.c
	gcc $< fractal.c -o $@ -Wall -Werror --std=c99 -lcrypt -lm -fopenmp -O3 -Dreally_parallel -Dshuffle
    
omp-dyn-shuffle: omp.c fractal.c
	gcc $< fractal.c -o $@ -Wall -Werror --std=c99 -lcrypt -lm -fopenmp -O3 -Dreally_parallel -Duse_dynamic -Dshuffle
    
mpi-omp-static: mpi_omp_static.c fractal.c measure.c
	mpicc $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm -mpe=mpilog -fopenmp -O3
	
mpi-omp-dyn: mpi_omp_dyn.c fractal.c measure.c
	mpicc $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm -mpe=mpilog -fopenmp -O3
	
clean:
	rm -rf mandelbrot mandelbrot-nodisp mpi-static mpi-dyn mpi-static-shuffle \
	mpi-dyn-shuffle omp-static omp-dyn omp-static-shuffle omp-dyn-shuffle \
	mpi-omp-static mpi-omp-dyn

no-data:
	rm -rf *.clog2 *.slog2

# vim: noexpandtab

