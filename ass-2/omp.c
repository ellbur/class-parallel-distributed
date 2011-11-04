
#define _XOPEN_SOURCE // for crypt()

#include <complex.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/time.h>
#include <omp.h>

#include "fractal.h"

static inline int row_at(int i) {
    return (i*373) % height;
}

int main(int argc, char **argv) {
    struct timeval start, end;
    bool in[height][width];
    
    gettimeofday(&start, NULL);
    
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
    
    gettimeofday(&end, NULL);
    double duration = (end.tv_sec-start.tv_sec)*1.0 + (end.tv_usec-start.tv_usec)/1e6;
    
    printf("[omp.compu] prog=%s,n=%d,time=%.5f\n",
        argv[0],
        omp_get_max_threads(),
        duration
    );
}

