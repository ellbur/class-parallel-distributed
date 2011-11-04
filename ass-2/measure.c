
#include "measure.h"

#include <mpi.h>
#include <stdio.h>

const char *program;

double useful_work = 0.0;
double mark = 0.0;

void begin_useful_work(void) {
    mark = MPI_Wtime();
}

void end_useful_work(void) {
    useful_work += MPI_Wtime() - mark;
}

void report_work(void) {
    int comm_size;
    MPI_Comm_size(MPI_COMM_WORLD, &comm_size);
    
    int proc;
    MPI_Comm_rank(MPI_COMM_WORLD, &proc);
    
    printf("[useful] prog=%s,n=%d,proc=%d,work=%.5f\n",
        program,
        comm_size,
        proc,
        useful_work
    );
}

double compu_start = 0.0;

void begin_computation(void) {
    compu_start = MPI_Wtime();
}

void report_computation(void) {
    int comm_size;
    MPI_Comm_size(MPI_COMM_WORLD, &comm_size);
    
    printf("[compu] prog=%s,n=%d,time=%.5f\n",
        program,
        comm_size,
        MPI_Wtime() - compu_start
    );
}

