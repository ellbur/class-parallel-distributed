
#define _XOPEN_SOURCE // for crypt()

#include <complex.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "fractal.h"

static inline int row_at(int i) {
    return (i*373) % height;
}

int main(int argc, char **argv) {
    bool in[height][width];
    
    #if really_parallel
        #if use_dynamic
            #pragma omp parallel for schedule(dynamic)
        #else
            #pragma omp parallel for schedule(static)
        #endif
    #endif
    for (int i=0; i<height; i++) {
        for (int j=0; j<width; j++) {
            #if shuffle
                in[row_at(i)][j] = calc_in(j, row_at(i));
            #else
                in[i][j] = calc_in(j, i);
            #endif
        }
    }
    
    char image_data[width*height*3];
    for (int i=0; i<height; i++)
    for (int j=0; j<width; j++) {
        int n = (i*width+j)*3;
        image_data[n+0] = in[i][j]-1;
        image_data[n+1] = in[i][j]-1;
        image_data[n+2] = in[i][j]-1;
    }
    
    print_hash(width*height*3, image_data);
}

