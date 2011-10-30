
#define _XOPEN_SOURCE // for crypt()

#include <mpi.h>
#include <complex.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define width  800
#define height 800

// ----------------------------------------------------------------
// Drawing code

const int iter_max = 100;

const double center_x = -0.78;
const double center_y = 0.0;
const double delta = 2.5;

double min_x;
double max_x;
double min_y;
double max_y;

double col_to_x(int col) { return (double)col/width*(max_x-min_x) + min_x; }
double row_to_y(int row) { return (double)row/height*(max_y-min_y) + min_y; }

double x_to_col(double x) { return (int)round((x-min_x)/(max_x-min_x)*width); }
double y_to_row(double y) { return (int)round((y-min_y)/(max_y-min_y)*height); }

static int calc_in(
    int col,
    int row
)
{
    double x = col_to_x(col);
    double y = row_to_y(row);
    
    complex double z0 = x + y*I;
    complex double z = z0;
    
    char in = true;
    
    for (int i=0; i<iter_max; i++) {
        if (cabs(z) > 16) {
            in = false;
            break;
        }
        
        z = z*z + z0;
    }
    
    return in;
}

// --------------------------------------------------------------------
// Write File

static void print_hash(int len, const char image_data[len]) {
    char nt_data[len];
    memcpy(nt_data, image_data, len);
    
    // The image data currently has many 0s. We need to make it
    // be a null-terminated string.
    for (int i=0; i<len; i++) {
        if (nt_data[i] == 0) {
            nt_data[i] = i & 0xFF;
        }
    }
    
    const char *hash = crypt(nt_data, "ab");
    printf("Hash: %s\n", hash);
}

// ----------------------------------------------------------------
// Parallelization Code

#define num_children 7

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
    system("uname -a");
    
    min_x = center_x - delta/2;
    max_x = center_x + delta/2;
    min_y = center_y - delta/2;
    max_y = center_y + delta/2;
    
    int row_start, row_stop, buf_len;
    calc_offsets(proc, &row_start, &row_stop, &buf_len);
    
    char buf[buf_len];
    for (int i=0; i<buf_len; i++) buf[i] = 0;
    
    for (int i=row_start; i<row_stop; i++)
    for (int j=0; j<height; j++) {
        int pix_off = (i-row_start)*width+j;
        buf[pix_off/8] |= calc_in(j, i) << (pix_off % 8);
    }
    MPI_Send(&buf, buf_len, MPI_CHAR, 0, 0, MPI_COMM_WORLD);
    MPI_Finalize();
}

// --------------------------------------------------------------------
// Main

int main(int argc, char **argv) {
    int proc;
    
    MPI_Init(&argc, &argv);
    MPI_Comm_rank(MPI_COMM_WORLD, &proc);
    
    if (proc == 0) {
        master_routine();
    }
    else child_routine(proc);
}

