
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
#define buf_len ((width+7)/8)

static void collect_data(char *image_data);
static void start_row(
    MPI_Request *pending_requests,
    int *pending_rows,
    char *buf,
    int row,
    int child
);
static void collect_into_image(char *image_data, int row, char *buf);
static void send_shutdown(int child);

static void master_routine() {
    char image_data[width * height * 3];
    
    begin_computation();
    
    collect_data(image_data);
    print_hash(width*height*3, image_data);
    
    report_computation();
    MPI_Finalize();
}

static void collect_data(char *image_data) {
    int buf_max_len = buf_len;
    char bufs[num_children][buf_max_len];
    
    MPI_Request pending_requests[num_children];
    int pending_rows[num_children];
    int sent_rows = 0;
    int completed_rows = 0;
    
    for (int i=0; i<num_children; i++) {
        start_row(pending_requests, pending_rows, bufs[i], i, i);
        sent_rows++;
    }
    
    while (completed_rows < height) {
        int recvd;
        MPI_Waitany(num_children, pending_requests, &recvd, MPI_STATUS_IGNORE);
        
        if (recvd == MPI_UNDEFINED) {
            fprintf(stderr, "For some reason we received no more messages...");
            exit(1);
        }
        collect_into_image(image_data, pending_rows[recvd], bufs[recvd]);
        completed_rows++;
        
        if (sent_rows < height) {
            start_row(pending_requests, pending_rows, bufs[recvd], sent_rows, recvd);
            sent_rows++;
        }
    }
    
    for (int i=0; i<num_children; i++) {
        send_shutdown(i);
    }
}

static void start_row(
    MPI_Request *pending_requests,
    int *pending_rows,
    char *buf,
    int row,
    int child
)
{
    MPI_Send(&row, 1, MPI_INT, child+1, 0, MPI_COMM_WORLD);
    
    MPI_Irecv(buf, buf_len, MPI_CHAR, child+1, 0,
        MPI_COMM_WORLD, pending_requests+child);
    pending_rows[child] = row;
}

static void send_shutdown(int child) {
    int msg = -1;
    MPI_Send(&msg, 1, MPI_INT, child+1, 0, MPI_COMM_WORLD);
}

static void collect_into_image(char *image_data, int row, char *buf) {
    for (int i=0; i<buf_len; i++)
    for (int j=0; j<8; j++) {
        int pix = row*width + i*8 + j;
        char in = (buf[i] >> j) & 1;
        
        image_data[3*pix+0] = in-1;
        image_data[3*pix+1] = in-1;
        image_data[3*pix+2] = in-1;
    }
}

static void child_routine(int proc) {
    begin_useful_work();
    
    while (true) {
        int row;
        MPI_Status status;
        
        end_useful_work();
        MPI_Recv(&row, 1, MPI_INT, 0, 0, MPI_COMM_WORLD, &status);
        if (row == -1) break;
        begin_useful_work();
        
        char buf[buf_len];
        for (int j=0; j<buf_len; j++) buf[j] = 0;
        for (int j=0; j<width; j++) {
            buf[j/8] |= calc_in(j, row) << (j % 8);
        }
        
        end_useful_work();
        MPI_Send(buf, buf_len, MPI_CHAR, 0, 0, MPI_COMM_WORLD);
        begin_useful_work();
    }
    
    report_work();
    MPI_Finalize();
}

int main(int argc, char **argv) {
    int proc;
    int comm_size;
    
    program = argv[0];
    
    MPI_Init(&argc, &argv);
    MPI_Comm_rank(MPI_COMM_WORLD, &proc);
    
    MPI_Comm_size(MPI_COMM_WORLD, &comm_size);
    num_children = comm_size-1;
    
    if (proc == 0) {
        master_routine();
    }
    else child_routine(proc);
}

