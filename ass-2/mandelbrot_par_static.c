
#define _XOPEN_SOURCE // for crypt()

#include <mpi.h>
#include <complex.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "fractal.h"
#include "measure.h"

int num_children;

static void calc_offsets(
    int proc,
    int *row_start,
    int *row_stop,
    int *buf_len
)
{
    int i = proc - 1;
    *row_start = (height+num_children-1)/num_children*i;
    
    if (i == num_children-1) *row_stop = height;
    else *row_stop = (height+num_children-1)/num_children*(i+1);
    
    *buf_len = (*row_stop - *row_start)*width/8;
}

static void collect_data(char *image_data);

static void master_routine() {
    char image_data[width * height * 3];
    
    collect_data(image_data);
    print_hash(width*height*3, image_data);
    MPI_Finalize();
}

static void collect_data(char *image_data) {
    int buf_max_len = (height+num_children-1)/num_children*width/8;
    char buf[buf_max_len];
    
    for (int p=1; p<(num_children+1); p++) {
        int row_start, row_stop, buf_len;
        calc_offsets(p, &row_start, &row_stop, &buf_len);
        
        MPI_Status status;
        MPI_Recv(&buf, buf_len, MPI_CHAR, p, 0, MPI_COMM_WORLD, &status);
        
        for (int i=row_start; i<row_stop; i++)
        for (int j=0; j<width; j++) {
            int pix = i*width+j;
            int pix_off = (i-row_start)*width+j;
            char in = (buf[pix_off/8] >> (pix_off%8)) & 1;
            
            image_data[3*pix+0] = in-1;
            image_data[3*pix+1] = in-1;
            image_data[3*pix+2] = in-1;
        }
    }
}

static void child_routine(int proc) {
    begin_useful_work();
    
    int row_start, row_stop, buf_len;
    calc_offsets(proc, &row_start, &row_stop, &buf_len);
    
    char buf[buf_len];
    for (int i=0; i<buf_len; i++) buf[i] = 0;
    
    for (int i=row_start; i<row_stop; i++)
    for (int j=0; j<width; j++) {
        int pix_off = (i-row_start)*width+j;
        buf[pix_off/8] |= calc_in(j, i) << (pix_off % 8);
    }
    
    end_useful_work();
    MPI_Send(&buf, buf_len, MPI_CHAR, 0, 0, MPI_COMM_WORLD);
    
    report_work();
    MPI_Finalize();
}

int main(int argc, char **argv) {
    int proc;
    int comm_size;
    
    MPI_Init(&argc, &argv);
    MPI_Comm_rank(MPI_COMM_WORLD, &proc);
    
    MPI_Comm_size(MPI_COMM_WORLD, &comm_size);
    num_children = comm_size-1;
    
    if (proc == 0) {
        master_routine();
    }
    else child_routine(proc);
}

