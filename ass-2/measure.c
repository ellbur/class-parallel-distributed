
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
    
    printf("Proc %d did %.3f useful work\n", proc, useful_work);
}

