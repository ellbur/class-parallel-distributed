
GTK_CFLAGS  = $(shell pkg-config --cflags gtk+-2.0)
GTK_LDFLAGS = $(shell pkg-config --libs gtk+-2.0)

gtktest: gtktest.c
	gcc gtktest.c -o $@ -Wall -Werror --std=c99 $(GTK_CFLAGS) $(GTK_LDFLAGS)

mandelbrot: mandelbrot.c
	gcc $< -o $@ -Wall --std=c99 $(GTK_CFLAGS) $(GTK_LDFLAGS) -lcrypt

mandelbrot_nodisp: mandelbrot_nodisp.c
	gcc $< -o $@ -Wall --std=c99 -lm -lcrypt

mstatic: mandelbrot_par_static.c fractal.c measure.c
	mpicc $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm

mdyn: mandelbrot_par_dynamic.c fractal.c measure.c
	mpicc $< fractal.c measure.c -o $@ -Wall --std=c99 -lcrypt -lm

clean:
	rm -rf gtktest mandelbrot mandelbrot_nodisp mstatic mdyn ref.bin

# vim: noexpandtab

