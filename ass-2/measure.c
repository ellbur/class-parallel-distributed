
#include "measure.h"

#include <mpi.h>
#include <stdio.h>

double useful_work = 0.0;
double mark = 0.0;

void begin_useful_work(void) {
    mark = MPI_Wtime();
}

void end_useful_work(void) {
    useful_work += MPI_Wtime() - mark;
}

void report_work(void) {
    int proc;
    MPI_Comm_rank(MPI_COMM_WORLD, &proc);
    
    printf("Proc %d did %.5f useful work\n", proc, useful_work);
}

double compu_start = 0.0;

void begin_computation(void) {
    compu_start = MPI_Wtime();
}

void report_computation(void) {
    printf("Computation took %.5f\n", MPI_Wtime() - compu_start);
}

